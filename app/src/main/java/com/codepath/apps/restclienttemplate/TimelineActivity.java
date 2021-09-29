package com.codepath.apps.restclienttemplate;

import static com.facebook.stetho.inspector.network.ResponseHandlingInputStream.TAG;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.codepath.apps.restclienttemplate.models.Tweet;
import com.codepath.apps.restclienttemplate.models.TweetDao;
import com.codepath.apps.restclienttemplate.models.TweetWithUser;
import com.codepath.apps.restclienttemplate.models.User;
import com.codepath.asynchttpclient.callback.JsonHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Headers;

public class TimelineActivity extends AppCompatActivity {
    TweetDao tweetDao;
    TwitterClient client;
    RecyclerView rvTweets;
    List<Tweet> tweets;
    TweetsAdapter adapter;
    SwipeRefreshLayout swipeContainer;
    EndlessRecyclerViewScrollListener scrollListener;

    public String TAG = "TimelineActivity";
    private final int REQUEST_CODE = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timeline);

        client = TwitterApp.getRestClient(this);
        tweetDao = ((TwitterApp) getApplicationContext()).getMyDatabase().tweetDao();

        swipeContainer = findViewById(R.id.swipeContainer);
        // Configure the refreshing colors
        swipeContainer.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Log.i(TAG, "fetching new data");
        //Every time we call this method, we add tweets to the bottom of our data set
        //We want to use the new methods that were added into adapter so first we clear out the data, then add in the needed new data that we get.
                populateHomeTimeline();
            }
        });

        //Find the recycler view;
        rvTweets = findViewById(R.id.rvTweets);
        //Initialize a list of tweets in adapter
        tweets = new ArrayList<>();
        adapter = new TweetsAdapter(this, tweets);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        //Recycler view setup: layout manager and the adapter
        rvTweets.setLayoutManager(new LinearLayoutManager(this));
        rvTweets.setAdapter(adapter);

        scrollListener = new EndlessRecyclerViewScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                Log.i(TAG, "onLoadMore" + page);
                LoadMoreData();

    //Call to get the next page of tweets to add to our current object of tweets.
            }
        };

        //Adds the scroll listener to RecyclerView
        rvTweets.addOnScrollListener(scrollListener);

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Showing data from database");
                List<TweetWithUser> tweetWithUsers = tweetDao.recentItems();
                List<Tweet> tweetsFromDB = TweetWithUser.getTweetList(tweetWithUsers);
                adapter.clear();
                adapter.addAll(tweetsFromDB);
            }
        });

        //Query for existing tweets in the DB

        populateHomeTimeline();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        if(item.getItemId() == R.id.compose){
            //Compose Icon selected
            Toast.makeText(this, "Compose", Toast.LENGTH_SHORT).show();
            //Navigate to the Compose activity
            Intent intent = new Intent(this, ComposeActivity.class);
            startActivityForResult(intent, REQUEST_CODE);       //This will launch the ComposeActivity
            return true;                   //Will give up back a tweet if the user actually submitted a tweet successfully.
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE && resultCode ==RESULT_OK){
            //Get data from the intent (the tweet object)
            Tweet tweet = Parcels.unwrap(data.getParcelableExtra("tweet"));
            //Update the RecycleView with this new tweet
            //Modify the data source of tweets
            tweets.add(0, tweet);
            //Update the adapter
            adapter.notifyItemInserted(0);
            //Call a method on the recycler view so we don't have to scroll up to see new tweet
            rvTweets.smoothScrollToPosition(0); //Because we want to go to the very top of the RV


            //We want the ComposeActivity to actually pass back the tweet object when the API call has succeede
        }
    }

    private void LoadMoreData() {
        // 1. Send an API request to retrieve appropriate paginated data
        // 2. Deserialize and construct new model objects from the API response
        // 3. Append the new data objects to the existing set of items inside the array of items
        // 4. Notify the adapter of the new items made with `notifyItemRangeInserted()`
    }

    private void populateHomeTimeline() {
        client.getHomeTimeline(new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Headers headers, JSON json) {
                Log.i(TAG, "onSuccess! "+ json.toString());
                JSONArray jsonArray = json.jsonArray;
                try {
                    //tweets.addAll(Tweet.fromJsonArray(jsonArray));
                    //adapter.notifyDataSetChanged();
                    List<Tweet> tweetsFromNetwork = Tweet.fromJsonArray(jsonArray);
                    adapter.clear();
                    adapter.addAll(tweetsFromNetwork);
            //Now we call setRefreshing(false) to signal refresh has finished
                    swipeContainer.setRefreshing(false);
                    AsyncTask.execute(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, "Saving data into database");
                            //insert users first
                            //Users need to be populated in the user table before the kwy connection can work.
                            List<User> usersFromNetwork = User.fromJsonTweetArray(tweetsFromNetwork);
                            tweetDao.insertModel(usersFromNetwork.toArray(new User[0]));
                            //Second, insert tweets
                            tweetDao.insertModel(tweetsFromNetwork.toArray(new Tweet[0]));
                        }
                    });
                } catch (JSONException e){
                    Log.e(TAG, "JSONException", e);
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(int statusCode, Headers headers, String response, Throwable throwable) {
                Log.e(TAG, "onFailure" + response, throwable);
            }
        });
    }
}