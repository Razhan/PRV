package com.bilibili.bbq.feedlib.binder;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bilibili.bbq.feedlib.viewholder.DataBindingViewHolder;
import com.bilibili.bbq.feedlib.viewholder.ViewHolder;

import java.util.List;

public abstract class DataBindingBinder<T, VH extends DataBindingViewHolder, BM extends BindingModel> extends BaseBinder<T, VH> {

    protected View buildView(@NonNull ViewGroup parent) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ViewDataBinding binding = DataBindingUtil.inflate(layoutInflater, getViewType(), parent, false);
        View view = binding.getRoot();
        view.setTag(binding);
        return view;
    }

    @Override
    public void bind(@NonNull T model, @NonNull VH holder, @NonNull List<Binder<? super T, ? extends ViewHolder>> binders,
                     int binderIndex, @NonNull List<Object> payloads) {
        super.bind(model, holder, binders,binderIndex, payloads);

        ViewDataBinding dataBinding = holder.getBinding();
        if (dataBinding == null) {
            return;
        }

        if (payloads.isEmpty()) {
            setAllDataBindingVariables(model, dataBinding);
        } else {
            //局部更新
            customUpdateActions(model, holder, binders,binderIndex, payloads);
            setUpdatedDataBindingVariables(model, dataBinding, payloads);
        }

        dataBinding.executePendingBindings();
    }

    @Override
    public void unbind(@NonNull VH holder) {

    }

    @Override
    public void prepare(@NonNull T model, List<Binder<? super T, ? extends ViewHolder>> binders, int binderIndex) {

    }

    //自定义局部更新操作，可以删除payloads中某项，后续更新将不会处理
    protected void customUpdateActions(@NonNull T model, @NonNull VH holder, @NonNull List<Binder<? super T, ? extends ViewHolder>> binders,
                                       int binderIndex, @NonNull List<Object> payloads) {

    }

    /**
     * 从真实数据模型中抽取需要展示的数据，轻量数据操作，注意每个Field都需要重新赋值，防止复用错误
     * 解耦数据解析与渲染逻辑
     */
    @NonNull
    protected abstract BM prepareBindingModel(Context context, @NonNull T model);

    protected abstract void setAllDataBindingVariables(@NonNull T model, ViewDataBinding binding);

    //payload值必须是生成的对应绑定数据模型静态成员
    protected abstract void setUpdatedDataBindingVariables(T model, ViewDataBinding binding, @NonNull List<Object> payloads);
}
