package io.dwak.holohackernews.app.ui.storydetail;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.melnykov.fab.FloatingActionButton;

import butterknife.ButterKnife;
import butterknife.InjectView;
import io.dwak.holohackernews.app.R;
import io.dwak.holohackernews.app.models.Comment;
import io.dwak.holohackernews.app.models.StoryDetail;
import io.dwak.holohackernews.app.preferences.UserPreferenceManager;
import io.dwak.holohackernews.app.ui.ViewModelFragment;
import io.dwak.holohackernews.app.util.HNLog;
import io.dwak.holohackernews.app.widget.ObservableWebView;
import io.dwak.holohackernews.app.widget.ReboundRevealRelativeLayout;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;


public class StoryDetailFragment extends ViewModelFragment<StoryDetailViewModel> implements ObservableWebView.OnScrollChangedCallback {
    public static final String HACKER_NEWS_ITEM_BASE_URL = "https://news.ycombinator.com/item?id=";
    public static final String HACKER_NEWS_BASE_URL = "https://news.ycombinator.com/";
    public static final String LINK_DRAWER_OPEN = "LINK_DRAWER_OPEN";
    public static final String TOP_VISIBLE_COMMENT = "TOP_VISIBLE_COMMENT";
    private static final String STORY_ID = "story_id";
    private static final String TAG = StoryDetailFragment.class.getSimpleName();
    @InjectView(R.id.prev_top_level) Button mPrevTopLevel;
    @InjectView(R.id.next_top_level) Button mNextTopLevel;
    @InjectView(R.id.web_back) Button mWebBack;
    @InjectView(R.id.close_link) Button mCloseLink;
    @InjectView(R.id.web_forward) Button mWebForward;
    @InjectView(R.id.swipe_container) SwipeRefreshLayout mSwipeRefreshLayout;
    @InjectView(R.id.comments_recycler) RecyclerView mCommentsRecyclerView;
    @InjectView(R.id.open_link) Button mOpenLinkDialogButton;
    @InjectView(R.id.story_web_view) ObservableWebView mWebView;
    @InjectView(R.id.link_layout) ReboundRevealRelativeLayout mLinkLayout;
    @InjectView(R.id.fabbutton) FloatingActionButton mFloatingActionButton;
    private long mStoryId;
    private int mPrevVisibleItem;
    //    private HeaderViewHolder mHeaderViewHolder;
    private Bundle mWebViewBundle;
    private boolean mReadability;
    private boolean mWasLinkLayoutOpen;
    private CommentsRecyclerAdapter mRecyclerAdapter;
    private LinearLayoutManager mLayoutManager;
    private View mHeaderView;
    private View mRootView;
    private ActionBar mActionBar;
    private int mCurrentFirstCompletelyVisibleItemIndex = 0;

    public static StoryDetailFragment newInstance(long param1) {
        StoryDetailFragment fragment = new StoryDetailFragment();
        Bundle args = new Bundle();
        args.putLong(STORY_ID, param1);
        fragment.setArguments(args);
        return fragment;
    }

    private void refresh() {
        showProgress(true);
        mSubscription = getViewModel().getStoryDetailObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<StoryDetail>() {
                    @Override
                    public void onCompleted() {
                        showProgress(false);
                        if (UserPreferenceManager.showLinkFirst(getActivity())
                                && UserPreferenceManager.isExternalBrowserEnabled(getActivity())) {
                            openLinkInExternalBrowser();
                        }
                        mSwipeRefreshLayout.setRefreshing(false);
                    }

                    @Override
                    public void onError(Throwable e) {
                        HNLog.d(TAG, e.getStackTrace()[0] + " : " + e.toString());
                        Toast.makeText(getActivity(), "Problem loading page", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onNext(StoryDetail storyDetail) {
                        HNLog.d(TAG, storyDetail.toString());
                        getViewModel().setStoryDetail(storyDetail);
                        updateWebView();
                        updateRecyclerView();
                    }
                });
    }

    private void updateRecyclerView() {
        mRecyclerAdapter.clear();
        updateHeader();
        for (Comment comment : getViewModel().getStoryDetail().getCommentList()) {
            mRecyclerAdapter.addComment(comment);
        }
    }

    private void updateWebView() {
        mOpenLinkDialogButton.setVisibility(View.VISIBLE);
        mOpenLinkDialogButton.setOnClickListener(view -> {
            if (!UserPreferenceManager.isExternalBrowserEnabled(getActivity())) {
                mLinkLayout.setOpen(!mLinkLayout.isOpen());
                StoryDetail storyDetail = getViewModel().getStoryDetail();
                if ("ask".equals(storyDetail.getType())) {
                    storyDetail.setUrl(HACKER_NEWS_BASE_URL + storyDetail.getUrl());
                }
                else if ("job".equals(storyDetail.getType())) {
                    if (storyDetail.getUrl().contains("/item?id=")) {
                        storyDetail.setUrl(HACKER_NEWS_BASE_URL + storyDetail.getUrl());
                    }
                }
            }
            else {
                openLinkInExternalBrowser();
            }
        });
    }

    private void updateHeader() {
        HeaderViewHolder mHeaderViewHolder = new HeaderViewHolder(mHeaderView);
        StoryDetail storyDetail = getViewModel().getStoryDetail();
        mHeaderViewHolder.mStoryTitle.setText(storyDetail.getTitle());
        mHeaderViewHolder.mStorySubmitter.setText(storyDetail.getUser());
        if (!"job".equals(storyDetail.getType())) {
            mHeaderViewHolder.mContent.setVisibility(View.GONE);
            if ("link".equals(storyDetail.getType()) && !TextUtils.isEmpty(storyDetail.getDomain())) {
                String domain = storyDetail.getDomain();
                mHeaderViewHolder.mStoryDomain.setVisibility(View.VISIBLE);
                mHeaderViewHolder.mStoryDomain.setText(" | " + domain.substring(0, 20 > domain.length() ? domain.length() : 20));
                if (UserPreferenceManager.showLinkFirst(getActivity()) && UserPreferenceManager.isExternalBrowserEnabled(getActivity())) {
                    openLinkInExternalBrowser();
                }
                else {
                    if (mWebViewBundle == null && !UserPreferenceManager.isExternalBrowserEnabled(getActivity())) {
                        mWebView.loadUrl(storyDetail.getUrl());
                    }
                    else {
                        mWebView.restoreState(mWebViewBundle);
                    }
                }
            }
            else {
                mHeaderViewHolder.mStoryDomain.setVisibility(View.GONE);

                mHeaderViewHolder.mContent.setVisibility(View.VISIBLE);
                Spanned jobContent = Html.fromHtml(storyDetail.getContent());
                mHeaderViewHolder.mContent.setMovementMethod(LinkMovementMethod.getInstance());
                mHeaderViewHolder.mContent.setText(jobContent);
                mHeaderViewHolder.mContent.setTextColor(getResources().getColor(UserPreferenceManager.isNightModeEnabled(getActivity()) ? android.R.color.white : android.R.color.black));
            }
            mHeaderViewHolder.mStoryPoints.setText(String.valueOf(storyDetail.getPoints()));
            mHeaderViewHolder.mStoryLongAgo.setText(" | " + storyDetail.getTimeAgo());
            mHeaderViewHolder.mCommentsCount.setText(storyDetail.getCommentsCount() + " comments");
        }
        else {
            mHeaderViewHolder.mContent.setVisibility(View.VISIBLE);
            Spanned jobContent = Html.fromHtml(storyDetail.getContent());
            mHeaderViewHolder.mContent.setMovementMethod(LinkMovementMethod.getInstance());
            mHeaderViewHolder.mContent.setTextColor(getResources().getColor(android.R.color.black));
            mHeaderViewHolder.mContent.setText(jobContent);
            mHeaderViewHolder.mStoryDomain.setVisibility(View.GONE);
            mHeaderViewHolder.mCommentsCount.setVisibility(View.GONE);
            mHeaderViewHolder.mStoryPoints.setVisibility(View.GONE);
        }

        mRecyclerAdapter.updateHeaderView(mHeaderViewHolder.mHeaderView);
    }

    private void openLinkInExternalBrowser() {
        Intent browserIntent = new Intent();
        browserIntent.setAction(Intent.ACTION_VIEW);
        browserIntent.setData(Uri.parse(getViewModel().getStoryDetail().getUrl()));
        startActivity(browserIntent);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        if (getArguments() != null) {
            mStoryId = getArguments().getLong(STORY_ID);
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mRootView = getRootView(inflater, container);
        if (savedInstanceState != null) {
            mWasLinkLayoutOpen = savedInstanceState.getBoolean(LINK_DRAWER_OPEN, false);
        }
        ButterKnife.inject(this, mRootView);
        getViewModel().setStoryId(mStoryId);
        mProgressBar = (ProgressBar) mRootView.findViewById(R.id.progress_bar);
        mContainer = mRootView.findViewById(R.id.container);
        mFloatingActionButton.setOnClickListener(view -> readability());
        setupWebViewDrawer();

        mPrevVisibleItem = 1;

        mActionBar = ((ActionBarActivity) getActivity()).getSupportActionBar();
        if (mActionBar != null) {
            mActionBar.show();
            mActionBar.setTitle("Hacker News");
        }

        mPrevTopLevel.setOnClickListener(view -> {
            for (int i = mCurrentFirstCompletelyVisibleItemIndex- 1; i >= 0; i--) {
                final Object item = mRecyclerAdapter.getItem(i);
                if (item instanceof Comment && ((Comment) item).getLevel() == 0) {
                    HNLog.d(TAG, String.valueOf(i));
                    mCurrentFirstCompletelyVisibleItemIndex = i;
                    mCommentsRecyclerView.smoothScrollToPosition(i);
                    return;
                }
            }
        });
        mNextTopLevel.setOnClickListener(view -> {
            for (int i = mCurrentFirstCompletelyVisibleItemIndex + 1; i < mRecyclerAdapter.getItemCount(); i++) {
                final Object item = mRecyclerAdapter.getItem(i);
                if (item instanceof Comment && ((Comment) item).getLevel() == 0) {
                    HNLog.d(TAG, String.valueOf(i));
                    mCurrentFirstCompletelyVisibleItemIndex = i;
                    mCommentsRecyclerView.smoothScrollToPosition(i);
                    return;
                }
            }
        });
        mRecyclerAdapter = new CommentsRecyclerAdapter(getActivity(), position -> {
            if (mRecyclerAdapter.areChildrenHidden(position)) {
                mRecyclerAdapter.showChildComments(position);
            }
            else {
                mRecyclerAdapter.hideChildComments(position);
            }
        });
        mLayoutManager = new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false);
        mCommentsRecyclerView.setLayoutManager(mLayoutManager);
        mCommentsRecyclerView.setAdapter(mRecyclerAdapter);
        mHeaderView = inflater.inflate(UserPreferenceManager.isNightModeEnabled(getActivity())
                ? R.layout.comments_header_dark
                : R.layout.comments_header,
                null);
        mRecyclerAdapter.addHeaderView(mHeaderView);

        mSwipeRefreshLayout.setColorSchemeResources(android.R.color.holo_orange_dark,
                android.R.color.holo_orange_light,
                android.R.color.holo_orange_dark,
                android.R.color.holo_orange_light);
        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            mSwipeRefreshLayout.setRefreshing(true);
            refresh();
        });

        refresh();
        return mRootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mWebViewBundle = savedInstanceState;
            mWebView.restoreState(mWebViewBundle);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mWebView.saveState(outState);
        outState.putBoolean(LINK_DRAWER_OPEN, mLinkLayout.isOpen());
//        outState.putInt(TOP_VISIBLE_COMMENT, mCommentsListView.getFirstVisiblePosition());
    }

    @Override
    public void onPause() {
        super.onPause();
        mWebViewBundle = new Bundle();
        mWebView.saveState(mWebViewBundle);
    }

    @Override
    public void onDetach() {
        if (mSubscription != null) mSubscription.unsubscribe();
        super.onDetach();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
        inflater.inflate(R.menu.story_detail, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_share:
                final CharSequence[] shareItems = {"Link", "Comments"};
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setItems(shareItems, (dialogInterface, i) -> {
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    switch (i) {
                        case 0:
                            sendIntent.putExtra(Intent.EXTRA_TEXT, getViewModel().getStoryDetail().getUrl());
                            break;
                        case 1:
                            sendIntent.putExtra(Intent.EXTRA_TEXT, HACKER_NEWS_ITEM_BASE_URL + mStoryId);
                            break;
                    }
                    sendIntent.setType("text/plain");
                    startActivity(sendIntent);
                });

                builder.create();
                builder.show();
                break;
            case R.id.action_open_browser:
                Intent browserIntent = new Intent();
                browserIntent.setAction(Intent.ACTION_VIEW);
                browserIntent.setData(Uri.parse(HACKER_NEWS_ITEM_BASE_URL + mStoryId));
                startActivity(browserIntent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupWebViewDrawer() {
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;

        mLinkLayout.setStashPixel(height);
        mLinkLayout.setRevealPixel(0);
        mLinkLayout.setTranslateDirection(ReboundRevealRelativeLayout.TRANSLATE_DIRECTION_VERTICAL);
        if (!UserPreferenceManager.isExternalBrowserEnabled(getActivity())) {
            mLinkLayout.setOpen(mWasLinkLayoutOpen || (UserPreferenceManager.showLinkFirst(getActivity())));
        }

        final ProgressBar webProgressBar = (ProgressBar) mLinkLayout.findViewById(R.id.web_progress_bar);
        mCloseLink.setOnClickListener(view -> mLinkLayout.setOpen(false));

        mWebView.setVisibility(View.INVISIBLE);
        webProgressBar.setVisibility(View.VISIBLE);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setJavaScriptEnabled(true);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                mWebView.setVisibility(View.VISIBLE);
                webProgressBar.setVisibility(View.GONE);
            }
        });
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                webProgressBar.setMax(100);
                webProgressBar.setProgress(newProgress);

            }
        });

        mWebView.setOnScrollChangedCallback(this);

        mWebBack.setOnClickListener(view -> {
            if (mWebView.canGoBack()) {
                mWebView.goBack();
            }
        });
        mWebForward.setOnClickListener(view -> {
            if (mWebView.canGoForward()) {
                mWebView.goForward();
            }
        });
    }

    public boolean isLinkViewVisible() {
        return mLinkLayout.isOpen();
    }

    public void hideLinkView() {
        mLinkLayout.setOpen(false);
    }

    private void readability() {
        mReadability = !mReadability;
        if (mReadability) {
            if(getViewModel().getReadabilityUrl() != null) {
                mWebView.loadUrl(getViewModel().getReadabilityUrl());
            }
        }
        else {
            mWebView.loadUrl(getViewModel().getStoryDetail().getUrl());
        }
    }

    @Override
    public void onScroll(int l, int t, int oldL, int oldT) {
        if (t >= oldT) {
            mFloatingActionButton.hide();
        }
        else {
            mFloatingActionButton.show();
        }
    }

    @Override
    protected Class<StoryDetailViewModel> getViewModelClass() {
        return StoryDetailViewModel.class;
    }

    @Override
    protected View getRootView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.fragment_story_comments, container, false);
    }
}
