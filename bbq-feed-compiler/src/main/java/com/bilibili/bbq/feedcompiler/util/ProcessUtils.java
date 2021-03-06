package com.bilibili.bbq.feedcompiler.util;

import com.bilibili.bbq.feedannotations.None;
import com.bilibili.bbq.feedannotations.PrvAdapter;
import com.bilibili.bbq.feedannotations.PrvAttribute;
import com.bilibili.bbq.feedannotations.PrvBinder;
import com.bilibili.bbq.feedannotations.PrvItemBinder;
import com.bilibili.bbq.feedannotations.PrvOnClick;
import com.bilibili.bbq.feedcompiler.info.AdapterInfo;
import com.bilibili.bbq.feedcompiler.info.BindingModelInfo;
import com.bilibili.bbq.feedcompiler.info.GeneratedModelInfo;
import com.bilibili.bbq.feedcompiler.info.ItemBinderInfo;
import com.bilibili.bbq.feedcompiler.info.ResourceInfo;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import static com.bilibili.bbq.feedcompiler.util.StringUtils.PATTERN_STARTS_WITH_SET;

public class ProcessUtils {

    private static Messager messager;
    private static Types types;
    private static Elements elements;

    private static List<String> excludedFields = new ArrayList<>(Arrays.asList("lifecycleOwner"));

    public static void init(Messager msger, Types typeUtils, Elements elementUtils) {
        messager = msger;
        types = typeUtils;
        elements = elementUtils;
    }

    @SuppressWarnings("ConstantConditions")
    public static Set<ItemBinderInfo> getItemBinderSet(RoundEnvironment env) {
        Set<ItemBinderInfo> itemBinderSet = new LinkedHashSet<>();

        for (Element element : env.getElementsAnnotatedWith(PrvItemBinder.class)) {
            if (!(element instanceof TypeElement)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Annotation PrvItemBinder should target on a Class");
            }

            itemBinderSet.add(getItemBinderInfo((TypeElement) element));
        }

        return itemBinderSet;
    }

    public static Map<TypeElement, Integer> getBinderMap(RoundEnvironment env) {
        Map<TypeElement, Integer> binderMap = new HashMap<>();

        for (Element element : env.getElementsAnnotatedWith(PrvBinder.class)) {
            if (!(element instanceof TypeElement)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Annotation PrvBinder should target on a Class");
                return null;
            }

            PrvBinder annotation = element.getAnnotation(PrvBinder.class);
            if (annotation != null) {
                if (binderMap.values().contains(annotation.value())) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "different binder can not has the same layout id");
                    return null;
                }

                binderMap.put((TypeElement) element, annotation.value());
            }
        }

        return binderMap;
    }

    public static Map<TypeElement, GeneratedModelInfo> getBinderInfoMap(Map<TypeElement, Integer> binderMap) {
        if (binderMap == null || binderMap.isEmpty()) {
            return null;
        }

        Map<TypeElement, GeneratedModelInfo> binderInfoMap = new HashMap<>();

        for (Map.Entry<TypeElement, Integer> entry : binderMap.entrySet()) {
            String rootPackage = ProcessUtils.getRootModuleString(entry.getKey());

            GeneratedModelInfo modelInfo = ProcessUtils.getGeneratedModelInfo(entry.getKey(),
                    ResourceUtils.getLayoutsInAnnotation(entry.getKey(), PrvBinder.class), rootPackage);

            binderInfoMap.put(entry.getKey(), modelInfo);
        }

        return binderInfoMap;
    }

    public static boolean waitingForDataBinding(Collection<GeneratedModelInfo> infoList) {
        for(GeneratedModelInfo info : infoList) {
            if (info == null) {
                continue;
            }

            if (getElementByName(info.dataBindingClassName) == null) {
                return true;
            }
        }

        return false;
    }

    public static void attributeGeneratedModel(Collection<GeneratedModelInfo> infoList) {
        for (GeneratedModelInfo info : infoList) {
            if (info == null) {
                continue;
            }

            TypeElement dataBinding = getElementByName(info.dataBindingClassName);
            List<BindingModelInfo> bindingModelInfo = new ArrayList<>();
            for (Element element : dataBinding.getEnclosedElements()) {
                if (element.getKind() != ElementKind.METHOD) {
                    continue;
                }

                if (ProcessUtils.isSetterMethod(element)) {
                    String name = StringUtils.removeSetPrefix(element.getSimpleName().toString());
                    TypeMirror typeMirror = ((ExecutableElement) element).getParameters().get(0).asType();
                    bindingModelInfo.add(new BindingModelInfo(name, typeMirror));
                }
            }

            info.setBindingModelInfo(bindingModelInfo);
        }
    }

    public static List<? extends TypeMirror> getClassGenericTypes(TypeElement element) {
        TypeElement typeElement = element;
        List<? extends TypeMirror> typeMirrors;

        List<? extends TypeMirror> interfaceTypes = typeElement.getInterfaces();
        if (interfaceTypes == null || interfaceTypes.isEmpty()) {
            while (true) {
                TypeMirror superClass = typeElement.getSuperclass();
                if (superClass.getKind() == TypeKind.NONE) {
                    return null;
                }

                typeMirrors = ((DeclaredType) superClass).getTypeArguments();

                if (typeMirrors == null || typeMirrors.isEmpty()) {
                    typeElement = (TypeElement) ((DeclaredType) superClass).asElement();
                } else {
                    return typeMirrors;
                }
            }
        } else {
            typeMirrors = ((DeclaredType) interfaceTypes.get(0)).getTypeArguments();
        }

        return typeMirrors;
    }

    public static Map<TypeElement, TypeElement> getAdapterList(RoundEnvironment env) {
        Map<TypeElement, TypeElement> adapterMap = new HashMap<>();

        for (Element element : env.getElementsAnnotatedWith(PrvAdapter.class)) {
            if (!(element instanceof TypeElement)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Annotation PrvAdapter should target on a Class");
                return null;
            }

            PrvAdapter annotation = element.getAnnotation(PrvAdapter.class);
            TypeElement dataTypeElement = null;
            try {
                annotation.value();
            } catch (MirroredTypeException ex) {
                dataTypeElement = (TypeElement) types.asElement(ex.getTypeMirror());
            }

            if (adapterMap.containsKey(dataTypeElement)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Annotation PrvAdapter should has different data type");
                return null;
            }
            adapterMap.put(dataTypeElement, (TypeElement) element);
        }

        return adapterMap;
    }

    public static String getRootModuleString(Element element) {
        PackageElement packageOf = elements.getPackageOf(element);
        String packageName = packageOf.getQualifiedName().toString() ;

        while (true) {
            if (packageName.lastIndexOf(".") > 0) {
                packageName = packageName.substring(0, packageName.lastIndexOf("."));

                Element rClass = getElementByName(packageName + ".R", elements, types);
                if (rClass != null) {
                    return packageName;
                }
            } else {
               break;
            }
        }

        return null;
    }

    public static Map<TypeElement, Set<BindingModelInfo>> getBindingModelSet(RoundEnvironment env) {
        Map<TypeElement, Set<BindingModelInfo>> BindingModelMap = new HashMap<>();

        for (Element element : env.getElementsAnnotatedWith(PrvAttribute.class)) {
            if (!(element instanceof VariableElement)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Annotation PrvAttribute should target on a Field");
            }

            TypeElement typeElement = (TypeElement) element.getEnclosingElement();
            Set<BindingModelInfo> attributeSet;
            if (BindingModelMap.containsKey(typeElement)) {
                attributeSet = BindingModelMap.get(typeElement);
            } else {
                attributeSet = new HashSet<>();
            }

            String fieldName = element.getSimpleName().toString();
            TypeMirror typeMirror = element.asType();

            attributeSet.add(new BindingModelInfo(fieldName, typeMirror));
            BindingModelMap.put(typeElement, attributeSet);
        }

        return BindingModelMap;
    }

    public static GeneratedModelInfo getGeneratedModelInfo(TypeElement typeElement, ResourceInfo resource, String rootPackage) {
        if (!isDataBindingBinder(typeElement)) {
            return null;
        }

        ClassName dataBindingClassName = getDataBindingClassNameForResource(resource, rootPackage);
        TypeElement dataBinding = getElementByName(dataBindingClassName);

        GeneratedModelInfo modelInfo = new GeneratedModelInfo();
        modelInfo.setPackageName(ClassName.get(typeElement).packageName());
        modelInfo.setClassName(ProcessUtils.getDataBindingClassNameStringForResource(resource));
        modelInfo.setDataBindingClassName(dataBindingClassName);

        //第一轮解析dataBinding为null
        if (dataBinding != null) {
            List<BindingModelInfo> bindingModelInfo = new ArrayList<>();
            for (Element element : dataBinding.getEnclosedElements()) {
                if (element.getKind() != ElementKind.METHOD) {
                    continue;
                }

                if (ProcessUtils.isSetterMethod(element)) {
                    String name = StringUtils.removeSetPrefix(element.getSimpleName().toString());
                    TypeMirror typeMirror = ((ExecutableElement) element).getParameters().get(0).asType();
                    bindingModelInfo.add(new BindingModelInfo(name, typeMirror));
                }
            }

            modelInfo.setBindingModelInfo(bindingModelInfo);
        }

        return modelInfo;
    }

    public static boolean isDataBindingBinder(TypeElement element) {
        TypeElement binderElement = element;

        while (true) {
            TypeMirror superClass = binderElement.getSuperclass();
            if (superClass.getKind() == TypeKind.NONE) {
                return false;
            }

            TypeElement parentElement = (TypeElement) ((DeclaredType) superClass).asElement();

            if (parentElement.toString().equals(NameStore.DATABINDING_BINDER)) {
                return true;
            } else {
                binderElement = parentElement;
            }
        }
    }

    public static List<AdapterInfo> getAdapterInfoList(Map<TypeElement, TypeElement> adapterList, Set<ItemBinderInfo> itemBinderInfoSet) {
        List<AdapterInfo> adapterInfoList = new ArrayList<>();
        for (Map.Entry<TypeElement, TypeElement> entry : adapterList.entrySet()) {
            TypeElement superType = entry.getKey();

            Set<ItemBinderInfo> infoSet = ProcessUtils.getCorrespondingAdapterInfo(superType, itemBinderInfoSet);
            adapterInfoList.add(new AdapterInfo(entry.getValue(), superType, infoSet));
        }

        return adapterInfoList;
    }

    private static boolean isSuperClass(TypeElement superElement, TypeElement childElement) {
        while (true) {
            if (superElement.toString().equals(childElement.toString())) {
                return true;
            } else {
                TypeMirror superClass = childElement.getSuperclass();
                if (superClass.getKind() == TypeKind.NONE) {
                    return false;
                }

                childElement = (TypeElement) ((DeclaredType) superClass).asElement();
            }
        }
    }


    private static boolean isInterface(TypeElement superElement, TypeElement childElement) {
        while (true) {
            List<? extends TypeMirror> interfaces = childElement.getInterfaces();
            if (interfaces != null && !interfaces.isEmpty()) {
                for (TypeMirror inter : interfaces) {
                    if (superElement.toString().equals(inter.toString())) {
                        return true;
                    }
                }
            }

            TypeMirror superClass = childElement.getSuperclass();
            if (superClass.getKind() == TypeKind.NONE) {
                return false;
            }

            childElement = (TypeElement) ((DeclaredType) superClass).asElement();
        }
    }

    public static List<Integer> getOnClickIds(TypeElement typeElement) {
        List<? extends Element> elementList = typeElement.getEnclosedElements();

        List<Integer> ids = new ArrayList<>();
        for (Element element : elementList) {
            //处理OnClick资源id
            if (element instanceof ExecutableElement) {
                ExecutableElement executableElement = (ExecutableElement) element;
                if (executableElement.getAnnotation(PrvOnClick.class) != null && executableElement.getAnnotation(Override.class) != null) {
                       if ("getListener".equals(executableElement.getSimpleName().toString()))  {
                           for (int id : executableElement.getAnnotation(PrvOnClick.class).value()) {
                               ids.add(id);
                           }
                           break;
                       }
                }
            }
        }

        return ids;
    }

    private static Set<ItemBinderInfo> getCorrespondingAdapterInfo(TypeElement superType, Set<ItemBinderInfo> itemBinderInfoSet) {
        Set<ItemBinderInfo> res = new HashSet<>();

        for (ItemBinderInfo info : itemBinderInfoSet) {
            if (isSuperClass(superType, elements.getTypeElement(info.dataType.toString()))) {
                res.add(info);
            } else if (isInterface(superType, elements.getTypeElement(info.dataType.toString()))) {
                res.add(info);
            }
        }

        return res;
    }

    // TODO: 10/16/18 简化逻辑用方法名判断，可能出现其他接口有相同方法名
    public static List<String> getUnOverrideMethodNames(TypeElement interfaceElement, TypeElement classElement) {
        List<String> interfaceMethods  = new ArrayList<>();
        for (Element element :  interfaceElement.getEnclosedElements()) {
            if (element.asType() instanceof ExecutableType) {
                interfaceMethods.add(element.getSimpleName().toString());
            }
        }

        interfaceMethods.removeAll(getOverrideMethodNames(interfaceElement, classElement));

        return interfaceMethods;
    }

    private static List<String> getOverrideMethodNames(TypeElement interfaceElement, TypeElement classElement) {
        List<String> interfaceMethods  = new ArrayList<>();
        for (Element element :  interfaceElement.getEnclosedElements()) {
            if (element.asType() instanceof ExecutableType) {
                interfaceMethods.add(element.getSimpleName().toString());
            }
        }

        List<String> overrideMethods  = new ArrayList<>();

        while (true) {
            TypeMirror superClass = classElement.getSuperclass();
            if (superClass.getKind() == TypeKind.NONE) {
                break;
            }

            for (Element element : classElement.getEnclosedElements()) {
                if (element.asType() instanceof ExecutableType && element.getAnnotation(Override.class) != null) {
                    overrideMethods.add(element.getSimpleName().toString());
                }
            }

            classElement = (TypeElement) ((DeclaredType) superClass).asElement();
        }

        List<String> res  = new ArrayList<>();
        for (String method : overrideMethods) {
            if (interfaceMethods.contains(method)) {
                res.add(method);
            }
        }

        return res;
    }

    private static ClassName getDataBindingClassNameForResource(ResourceInfo info, String moduleName) {
        String modelName = StringUtils.toUpperCamelCase(info.resourceName).concat(NameStore.BINDING_SUFFIX);

        return ClassName.get(moduleName.concat("." + NameStore.DATA_BINDING), modelName);
    }

    private static String getDataBindingClassNameStringForResource(ResourceInfo info) {
        String[] strArray = info.resourceName.split("_");
        //取最后一个Word + BindingModel
        return StringUtils.toUpperCamelCase(strArray[strArray.length - 1]).concat(NameStore.BINDING_MODEL_SUFFIX);
    }

    private static TypeElement getElementByName(ClassName name) {
        String canonicalName = name.reflectionName().replace("$", ".");
        return (TypeElement) getElementByName(canonicalName, elements, types);
    }

    private static boolean isSetterMethod(Element element) {
        if (element.getKind() != ElementKind.METHOD) {
            return false;
        }

        ExecutableElement method = (ExecutableElement) element;
        String methodName = method.getSimpleName().toString();

        return !excludedFields.contains(methodName) && PATTERN_STARTS_WITH_SET.matcher(methodName).matches()
                && method.getParameters().size() == 1;
    }

    private static ItemBinderInfo getItemBinderInfo(TypeElement element) {
        List<? extends TypeMirror> interfaces = element.getInterfaces();

        boolean hasItemBinderInterface = false;
        List<String> overrideMethods = new ArrayList<>();
        List<TypeElement> binders = new ArrayList<>();
        TypeName dataType;

        if (interfaces != null && !interfaces.isEmpty()) {
            for (TypeMirror interfaceType : interfaces) {
                TypeElement interfaceElement = (TypeElement) ((DeclaredType) interfaceType).asElement();
                if (interfaceElement.toString().equals(NameStore.ITEM_BINDER)) {
                    hasItemBinderInterface = true;
                    overrideMethods = getOverrideMethodNames(interfaceElement, element);
                    break;
                }
            }
        }

        PrvItemBinder annotation = element.getAnnotation(PrvItemBinder.class);
        try {
            annotation.binder();
        } catch (MirroredTypesException ex) {
            List<? extends TypeMirror> binderTypes = ex.getTypeMirrors();
            for (TypeMirror binderType : binderTypes) {
                binders.add((TypeElement) types.asElement(binderType));
            }

            if (overrideMethods.isEmpty() && binders.isEmpty()) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Annotation PrvItemBinder must has corresponding binders");
                return null;
            }
        }

        try {
            dataType = ClassName.get(annotation.type());
        } catch (MirroredTypeException ex) {
            dataType = ClassName.get(ex.getTypeMirror());
        }

        if (dataType.toString().equals(None.class.getName())) {
            List<? extends TypeMirror> genericTypes = getClassGenericTypes(element);
            if (genericTypes == null || genericTypes.isEmpty()) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Annotation PrvItemBinder must has a corresponding data type");
                return null;
            }
            dataType = ClassName.bestGuess(genericTypes.get(0).toString());
        }

        return new ItemBinderInfo(element, dataType, binders, hasItemBinderInterface, overrideMethods);
    }

    private static Element getElementByName(String name, Elements elements, Types types) {
        try {
            return elements.getTypeElement(name);
        } catch (MirroredTypeException mte) {
            return types.asElement(mte.getTypeMirror());
        }
    }


}