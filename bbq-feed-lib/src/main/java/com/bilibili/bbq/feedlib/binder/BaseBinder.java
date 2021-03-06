package com.bilibili.bbq.feedlib.binder;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ViewGroup;

import com.bilibili.bbq.feedlib.BasePrvAdapter;
import com.bilibili.bbq.feedlib.listener.ActionListener;
import com.bilibili.bbq.feedlib.viewholder.BaseViewHolder;
import com.bilibili.bbq.feedlib.viewholder.ViewHolder;

import java.util.List;

public abstract class BaseBinder<T, VH extends BaseViewHolder> implements Binder<T, VH> {

    private BasePrvAdapter mAdapter;

    @Nullable
    private ActionListener<T, VH> mListener;

    public BaseBinder() {
        mListener = getListener();
    }

    protected abstract VH create(ViewGroup parent, ActionListener<T, VH> listener);

    @Override
    public VH create(ViewGroup parent) {
        return create(parent, mListener);
    }

    @Override
    @CallSuper
    public void bind(@NonNull T model, @NonNull VH holder, @NonNull List<Binder<? super T, ? extends ViewHolder>> binders, int binderIndex, @NonNull List<Object> payloads) {
        updateListener(model, holder, binders, binderIndex);
    }

    @SuppressWarnings("unchecked")
    private void updateListener(T model, VH holder, List<Binder<? super T, ? extends ViewHolder>> binders, int binderIndex) {
        holder.getListenerDelegate().update(model, holder, binders, binderIndex);
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {

    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {

    }

    /**
     * 复写此方法时，注意使用@PrvOnClick标记View Id
     */
    @Nullable
    protected ActionListener<T, VH> getListener() {
        return null;
    }

    @NonNull
    public BasePrvAdapter getAdapter() {
        return mAdapter;
    }

    public Binder<T, VH> setAdapter(@NonNull BasePrvAdapter adapter) {
        this.mAdapter = adapter;
        return this;
    }
}
