package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.ApiConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Hot;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Suggest;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityCollectBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.SiteCallback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.ui.adapter.VodAdapter;
import com.fongmi.android.tv.ui.adapter.WordAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.custom.dialog.SiteDialog;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Utils;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Response;

public class CollectActivity extends BaseActivity implements CustomScroller.Callback, SiteCallback, WordAdapter.OnClickListener, RecordAdapter.OnClickListener, CollectAdapter.OnClickListener, VodAdapter.OnClickListener {

    private ActivityCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private RecordAdapter mRecordAdapter;
    private ExecutorService mExecutor;
    private WordAdapter mWordAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private VodAdapter mVodAdapter;
    private List<Site> mSites;

    public static void start(Activity activity) {
        start(activity, "");
    }

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mScroller = new CustomScroller(this);
        mSites = new ArrayList<>();
        setRecyclerView();
        setLayoutSize();
        setViewModel();
        checkKeyword();
        setViewType();
        setSite();
        getHot();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.site.setOnClickListener(this::onSite);
        mBinding.view.setOnClickListener(this::toggleView);
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) search();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().isEmpty()) getHot();
                else getSuggest(s.toString());
            }
        });
    }

    private void setRecyclerView() {
        mBinding.collect.setHasFixedSize(true);
        mBinding.collect.setItemAnimator(null);
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this));
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.setAdapter(mVodAdapter = new VodAdapter(this));
        mBinding.wordRecycler.setHasFixedSize(true);
        mBinding.wordRecycler.setAdapter(mWordAdapter = new WordAdapter(this));
        mBinding.recordRecycler.setHasFixedSize(true);
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
        mVodAdapter.setSize(Product.getSpec(this, ResUtil.dp2px(64), 3));
    }

    private void setViewType() {
        mVodAdapter.setViewType(Setting.getViewType());
        boolean grid = mVodAdapter.getViewType() == ViewType.GRID;
        GridLayoutManager manager = (GridLayoutManager) mBinding.recycler.getLayoutManager();
        mBinding.view.setImageResource(grid ? R.drawable.ic_action_list : R.drawable.ic_action_grid);
        manager.setSpanCount(grid ? 2 : 1);
    }

    private void setLayoutSize() {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mBinding.collect.getLayoutParams();
        params.width = mVodAdapter.getWidth() + ResUtil.dp2px(24);
        mBinding.collect.setLayoutParams(params);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.search.observe(this, result -> {
            if (mCollectAdapter.getPosition() == 0) mVodAdapter.addAll(result.getList());
            mCollectAdapter.add(Collect.create(result.getList()));
            mCollectAdapter.add(result.getList());
        });
        mViewModel.result.observe(this, result -> {
            boolean same = result.getList().size() > 0 && mCollectAdapter.getActivated().getSite().equals(result.getList().get(0).getSite());
            if (same) mCollectAdapter.getActivated().getList().addAll(result.getList());
            if (same) mVodAdapter.addAll(result.getList());
            mScroller.endLoading(result);
        });
    }

    private void checkKeyword() {
        if (TextUtils.isEmpty(getKeyword())) mBinding.keyword.requestFocus();
        else setKeyword(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
    }

    private void setSite() {
        for (Site site : ApiConfig.get().getSites()) if (site.isSearchable()) mSites.add(site);
        Site home = ApiConfig.get().getHome();
        if (!mSites.contains(home)) return;
        mSites.remove(home);
        mSites.add(0, home);
    }

    private void search() {
        if (empty()) return;
        mVodAdapter.clear();
        mCollectAdapter.clear();
        Utils.hideKeyboard(mBinding.keyword);
        mBinding.site.setVisibility(View.GONE);
        mBinding.agent.setVisibility(View.GONE);
        mBinding.view.setVisibility(View.VISIBLE);
        mBinding.result.setVisibility(View.VISIBLE);
        if (mExecutor != null) mExecutor.shutdownNow();
        mExecutor = Executors.newFixedThreadPool(Constant.THREAD_POOL * 2);
        String keyword = mBinding.keyword.getText().toString().trim();
        for (Site site : mSites) mExecutor.execute(() -> search(site, keyword));
        App.post(() -> mRecordAdapter.add(keyword), 250);
    }

    private void search(Site site, String keyword) {
        try {
            mViewModel.searchContent(site, keyword, false);
        } catch (Throwable ignored) {
        }
    }

    private void getHot() {
        mBinding.word.setText(R.string.search_hot);
        mWordAdapter.addAll(Hot.get(Setting.getHot()));
    }

    private void getSuggest(String text) {
        mBinding.word.setText(R.string.search_suggest);
        OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + text).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<String> items = Suggest.get(response.body().string());
                App.post(() -> mWordAdapter.addAll(items));
            }
        });
    }

    private void onSite(View view) {
        Utils.hideKeyboard(mBinding.keyword);
        App.post(() -> SiteDialog.create(this).search().show(), 50);
    }

    private void toggleView(View view) {
        mVodAdapter.setViewType(mVodAdapter.getViewType() == ViewType.GRID ? ViewType.LIST : ViewType.GRID);
        Setting.putViewType(mVodAdapter.getViewType());
        setViewType();
    }

    private void showAgent() {
        mScroller.reset();
        mVodAdapter.clear();
        mCollectAdapter.clear();
        mBinding.view.setVisibility(View.GONE);
        mBinding.result.setVisibility(View.GONE);
        mBinding.site.setVisibility(View.VISIBLE);
        mBinding.agent.setVisibility(View.VISIBLE);
        if (mExecutor != null) mExecutor.shutdownNow();
    }

    @Override
    public void setSite(Site item) {
    }

    @Override
    public void onChanged() {
        mSites.clear();
        setSite();
    }

    @Override
    public void onItemClick(String text) {
        setKeyword(text);
        search();
    }

    @Override
    public void onDataChanged(int size) {
        mBinding.record.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        mBinding.recordRecycler.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        App.post(() -> mBinding.recordRecycler.requestLayout(), 250);
    }

    @Override
    public void onItemClick(int position, Collect item) {
        mBinding.recycler.scrollToPosition(0);
        mCollectAdapter.setActivated(position);
        mVodAdapter.clear().addAll(item.getList());
        mScroller.setPage(item.getPage());
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isFolder()) VodActivity.start(this, item.getSiteKey(), Result.folder(item));
        else DetailActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName());
    }

    @Override
    public boolean onLongClick(Vod item) {
        return false;
    }

    @Override
    public void onLoadMore(String page) {
        Collect activated = mCollectAdapter.getActivated();
        if (activated.getSite().getKey().equals("all")) return;
        mViewModel.searchContent(activated.getSite(), mBinding.keyword.getText().toString(), page);
        activated.setPage(Integer.parseInt(page));
        mScroller.setLoading(true);
    }

    @Override
    public void onBackPressed() {
        if (isVisible(mBinding.result)) {
            showAgent();
        } else {
            super.onBackPressed();
        }
    }
}
