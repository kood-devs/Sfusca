package stemonitis.fusca;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public final class HeadlineActivity extends AppCompatActivity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 2000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private View contentView;

    private static int AUTO_SCROLL_DELAY = 15000;
    private static int SCROLL_DURATION = 1500;

    private List<Medium> mediaList;
    private ListIterator<Medium> mediaIterator;
    private Medium medium;
    private TextView title;
    private ListView headlineListView;
    private boolean headlineIsReady;
    private boolean isActive;

    /**
     * 2 handlers are handled in this activity.
     * reloadHandler handles reload of the news.
     * uiHandler handles UIs, which include visibility of action bar,
     * switching the medium of headline, and scrolling of headline.
     */
    private Handler reloadHandler = new Handler();
    private Handler uiHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        getSupportActionBar().hide();
        View decorView = getWindow().getDecorView();
// Hide both the navigation bar and the status bar.
// SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
// a general rule, you should design your app to hideBars the status bar whenever you
// hideBars the navigation bar.
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        decorView.setSystemUiVisibility(uiOptions);

        setContentView(R.layout.activity_headline);

        barsAreVisible = true;
        contentView = findViewById(R.id.lHeadline);

        /**
         * Touch listener to use for in-layout UI controls to delay hiding the
         * system UI. This is to prevent the jarring behavior of controls going away
         * while interacting with activity UI.
         */
        contentView.setOnTouchListener(new OnSwipeTouchListener(HeadlineActivity.this) {
                    @Override
                    public void onPresumedClick(){
                        toggleUiVisibility();
                    }

                    @Override
                    public void onSwipeRight(){
                        previousHeadline();
                    }

                    @Override
                    public void onSwipeLeft(){
                        nextHeadline();
                    }
                });

        contentView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener(){
                    @Override
                    public void onSystemUiVisibilityChange(int visibility){
                        if(isActive){
                            Log.i("HeadlineActivity", "VisibilityChange" + String.valueOf(visibility));
                            if (visibility == View.SYSTEM_UI_FLAG_VISIBLE){
                                showBars();
                            }else{
                                hideBars();
                            }
                        }
                    }
                });


        mediaList = new ArrayList<>();
        mediaList.add(new Nikkei(20));
        mediaList.add(new Reuters(10));
        mediaList.add(new SZ(10));
        mediaList.add(new TechCrunch(10));
        headlineReload();

        mediaIterator = mediaList.listIterator();
        if(mediaIterator.hasNext()) {
            medium = mediaIterator.next();
        }

        title = findViewById(R.id.tvHeadlineTitle);
        title.setText(medium.getName());

        headlineListView = findViewById(R.id.lvHeadline);
        headlineListView.setVerticalScrollBarEnabled(false);
        headlineListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                goToArticlesFrom(position);
            }
        });

        headlineIsReady = false;
        reloadHandler.postDelayed(headlineSetter, RELOAD_CHECK_INTERVAL);

        isActive = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.menu_context_headline, menu);
        return true;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hideBars() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private static int RELOAD_CHECK_INTERVAL = 200;

    private Runnable headlineSetter = new Runnable() {
        @Override
        public void run() {
            if(!medium.isReloading()){
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(HeadlineActivity.this,
                        android.R.layout.simple_list_item_1, medium.getList()){
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        TextView view = (TextView)super.getView(position, convertView, parent);
                        view.setTextSize( 45 );
                        return view;
                    }
                };
                headlineListView.setAdapter(adapter);
                headlineIsReady = true;
                uiHandler.removeCallbacksAndMessages(null);
                uiHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
            }else{
                headlineIsReady = false;
                reloadHandler.removeCallbacksAndMessages(null);
                reloadHandler.postDelayed(headlineSetter, RELOAD_CHECK_INTERVAL);
            }
        }
    };

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        uiHandler.removeCallbacksAndMessages(null);
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                uiHandler.removeCallbacksAndMessages(null);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                uiHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
                break;
        }

        return super.dispatchTouchEvent(ev);
    }


    private int scrollBy=0; // set value in canScroll()

    private final Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (canScroll()) {
                headlineListView.smoothScrollBy(scrollBy, SCROLL_DURATION);
                uiHandler.removeCallbacksAndMessages(null);
                uiHandler.postDelayed(this, AUTO_SCROLL_DELAY);
            } else if(headlineIsReady){
                Log.i("HeadlineActivity", "can't scroll");
                uiHandler.removeCallbacksAndMessages(null);
                uiHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        goToArticlesFrom(0);
                    }
                }, AUTO_SCROLL_DELAY);
            }
        }
    };

    private boolean canScroll(){
        int lastIndex = headlineListView.getLastVisiblePosition()
                - headlineListView.getFirstVisiblePosition();
        View c = headlineListView.getChildAt(lastIndex);
        if (c!=null){
            // scroll distance is roughly the height of display
            scrollBy = c.getHeight() * lastIndex;
            return (headlineListView.getLastVisiblePosition() < headlineListView.getAdapter().getCount()-1)
                    || (c.getBottom() > headlineListView.getHeight());
        }else{
            return false;
        }
    }


    /**
     * Handle the visibility of bars (action bar, navigation bar, etc).
     * Every process ends up with start of auto scroll.
     */
    private boolean barsAreVisible;

    private final Runnable setVisibilityRunnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            contentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
//                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            uiHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
        }
    };

    private void hideBars() {
        // Hide UI first
        getSupportActionBar().hide();
        barsAreVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        uiHandler.removeCallbacksAndMessages(null);
        uiHandler.postDelayed(setVisibilityRunnable, UI_ANIMATION_DELAY);
        // -> autoScroll
    }

    private final Runnable hideBarsRunnable = new Runnable() {
        @Override
        public void run() {
            hideBars();
            // -> setVisibility -> autoScroll
        }
    };

    /**
     * Schedules a call to hideBars() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        uiHandler.removeCallbacksAndMessages(null);
        uiHandler.postDelayed(hideBarsRunnable, delayMillis);
        // -> setVisibility -> autoScroll
    }

    private final Runnable showActionBarRunnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            getSupportActionBar().show();
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
                // -> hideBars -> setVisibility -> autoScroll
            }else{
                uiHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
            }
        }
    };

    @SuppressLint("InlinedApi")
    private void showBars() {
        barsAreVisible = true;

        // Schedule a runnable to display UI elements after a delay
        uiHandler.removeCallbacksAndMessages(null);
        uiHandler.postDelayed(showActionBarRunnable, UI_ANIMATION_DELAY);
        // -> ... -> autoScroll
    }

    private void toggleUiVisibility(){
        uiHandler.removeCallbacksAndMessages(null);
        if (barsAreVisible) {
            hideBars();
            // -> ... -> autoScroll
        } else {
            showBars();
            // -> ... -> autoScroll
        }
    }


    private void headlineReload(){
        new AsyncReloader(mediaList).execute();
    }


    private void previousHeadline(){
        if(!medium.isReloading()) {
            Medium medium0 = medium;
            new AsyncReloader(medium0).execute();
        }
        if (mediaIterator.hasPrevious()) {
            medium = mediaIterator.previous();
        } else {
            while (mediaIterator.hasNext()) {
                medium = mediaIterator.next();
            }
        }
        headlineIsReady = false;
        title.setText(medium.getName());
        uiHandler.removeCallbacksAndMessages(null);
        reloadHandler.removeCallbacksAndMessages(null);
        reloadHandler.postDelayed(headlineSetter, RELOAD_CHECK_INTERVAL);
    }

    private void nextHeadline(){
        if(!medium.isReloading()) {
            Medium medium0 = medium;
            new AsyncReloader(medium0).execute();
        }
        if (mediaIterator.hasNext()) {
            medium = mediaIterator.next();
        } else {
            while (mediaIterator.hasPrevious()) {
                medium = mediaIterator.previous();
            }
        }
        headlineIsReady = false;
        title.setText(medium.getName());
        uiHandler.removeCallbacksAndMessages(null);
        reloadHandler.removeCallbacksAndMessages(null);
        reloadHandler.postDelayed(headlineSetter, RELOAD_CHECK_INTERVAL);
    }


    private void goToArticlesFrom(int index){
        Log.i("HeadlineActivity", "goToArticlesFrom" + index);
        if(medium.getArticles().size()>0) {
            List<Article> aList = medium.getArticles().subList(index, medium.articles.size());
            ArrayList<String> titles = new ArrayList<>();
            ArrayList<String> contents = new ArrayList<>();

            for (Article a : aList) {
                titles.add(a.getTitle());
                contents.add(a.getContent());
            }

            Intent intent = new Intent(HeadlineActivity.this, ArticleActivity.class);
            intent.putExtra("autoChange", true);
            intent.putStringArrayListExtra("titles", titles);
            intent.putStringArrayListExtra("contents", contents);
            startActivityForResult(intent, 0);
        }else{
            nextHeadline();
        }
    }


    @Override
    public void onResume(){
        super.onResume();
        hideBars();

        uiHandler.removeCallbacksAndMessages(null);
        uiHandler.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);

        isActive = true;
    }

    @Override
    public void onPause(){
        isActive = false;

        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i("onActivityResult", "aaa");
        super.onActivityResult(requestCode, resultCode, data);
        if(data.getBooleanExtra("nextHeadline", false)) {
            uiHandler.removeCallbacksAndMessages(null);
            nextHeadline();
        }
    }
}
