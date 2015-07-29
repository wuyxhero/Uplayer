package com.github.sachil.uplayer.ui;

import org.fourthline.cling.model.ModelUtil;
import org.fourthline.cling.support.model.PlayMode;
import org.fourthline.cling.support.model.PositionInfo;
import org.fourthline.cling.support.model.TransportState;

import android.graphics.Bitmap;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.controller.BaseControllerListener;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.image.ImageInfo;
import com.github.sachil.uplayer.R;
import com.github.sachil.uplayer.ui.message.PlayerMessage;
import com.github.sachil.uplayer.upnp.UpnpUnity;
import com.github.sachil.uplayer.upnp.dmc.Controller;
import com.github.sachil.uplayer.upnp.dmc.XMLToMetadataParser.Metadata;

import java.util.Timer;
import java.util.TimerTask;

import de.greenrobot.event.EventBus;

public class PlayerActivity extends AppCompatActivity
		implements View.OnClickListener {
	private static final String TAG = PlayerActivity.class.getSimpleName();

	private View mBackground = null;
	private Toolbar mToolbar = null;
	private TextView mTitle = null;
	private TextView mArtist = null;
	private TextView mAlbum = null;
	private SimpleDraweeView mThumb = null;
	private TextView mCurrentTime = null;
	private TextView mTotalTime = null;
	private SeekBar mSeekBar = null;
	private ImageView mModeButton = null;
	private ImageView mPrevButton = null;
	private ImageView mPlayPauseButton = null;
	private ImageView mNextButton = null;
	private ImageView mListButton = null;

	private Timer mPositionTimer = null;
	private TimerTask mPositionTask = null;
	private Controller mController = null;
	private int mBackgroundColor = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player_activity);
		initView();

		if (UpnpUnity.UPNP_SERVICE != null
				&& UpnpUnity.CURRENT_RENDERER != null)
			mController = new Controller(this, UpnpUnity.UPNP_SERVICE,
					UpnpUnity.CURRENT_RENDERER);
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!EventBus.getDefault().isRegistered(this))
			EventBus.getDefault().register(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		if (EventBus.getDefault().isRegistered(this))
			EventBus.getDefault().unregister(this);
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.player_model:

			break;
		case R.id.player_prev:

			break;
		case R.id.player_play_pause:

			break;
		case R.id.player_next:

			break;
		case R.id.player_playlist:

			break;
		}
	}

	public void onEventMainThread(PlayerMessage message) {

		switch (message.getId()) {
		case PlayerMessage.REFRESH_METADATA:
			Metadata metadata = (Metadata) message.getExtra();
			syncState(PlayerMessage.REFRESH_METADATA, metadata);
			break;

		case PlayerMessage.REFRESH_VOLUME:
			metadata = (Metadata) message.getExtra();
			syncState(PlayerMessage.REFRESH_VOLUME, metadata);
			break;

		case PlayerMessage.REFRESH_CURRENT_POSITION:

			PositionInfo info = (PositionInfo) message.getExtra();
			syncState(PlayerMessage.REFRESH_CURRENT_POSITION, info);
			break;
		}

	}

	private void syncState(int id, Object data) {

		if (id == PlayerMessage.REFRESH_METADATA) {
			Metadata metadata = (Metadata) data;
			TransportState state = metadata.getState();
			if (state == TransportState.PLAYING) {
				mPlayPauseButton
						.setImageResource(R.drawable.ic_action_playback_pause);
				mTitle.setText(metadata.getTitle());
				mArtist.setText(metadata.getArtist());
				mAlbum.setText(metadata.getAlbum());
				mTotalTime.setText(metadata.getDuration());

				/**
				 * 当前播放的时间位置不会通过Lastchange事件触发得到，所以需要
				 * 单独启动一个线程以一定的频率(通常是1s)去不断的获取当前的播放位置，
				 * 该线程只会在transportState为PLAYING状态时才启动。
				 */
				mPositionTimer = new Timer();
				mPositionTask = new TimerTask() {

					@Override
					public void run() {
						// TODO Auto-generated method stub
						mController.getPosition();
					}
				};
				mPositionTimer.schedule(mPositionTask, 0, 1000);

				PlayMode mode = metadata.getPlayMode();
				if (mode == PlayMode.NORMAL)
					mModeButton.setImageResource(
							R.drawable.ic_action_playback_repeat);
				else if (mode == PlayMode.REPEAT_ONE)
					mModeButton.setImageResource(
							R.drawable.ic_action_playback_repeat_1);
				else if (mode == PlayMode.SHUFFLE)
					mModeButton.setImageResource(
							R.drawable.ic_action_playback_schuffle);

				if (metadata.getAlbumArt() != null) {
					mThumb.setController(Fresco.newDraweeControllerBuilder()
							.setControllerListener(
									new BaseControllerListener<ImageInfo>() {

										@Override
										public void onFinalImageSet(String id,
												ImageInfo imageInfo,
												Animatable animatable) {

											mThumb.setDrawingCacheEnabled(true);
											int defaultColor = getResources()
													.getColor(
															R.color.half_transparent);

											/**
											 * 有时候palette一次解析并不能得到正确的颜色,所以这里尝试3次
											 */
											int i = 0;
											do {
												generateBackgroundColor(mThumb
														.getDrawingCache());
												i++;
											} while (i < 3
													&& mBackgroundColor == defaultColor);
											mBackground.setBackgroundColor(
													mBackgroundColor);
											mThumb.setDrawingCacheEnabled(
													false);
										}
									})
							.setUri(Uri.parse(metadata.getAlbumArt())).build());
				}
			} else {
				mPlayPauseButton
						.setImageResource(R.drawable.ic_action_playback_play);

				// transportState不是PLAYING状态时,停止获取当前播放的位置信息。
				if (mPositionTimer != null)
					mPositionTimer.cancel();
				mCurrentTime.setText("00:00:00");
				mSeekBar.setProgress(0);
			}
		} else if (id == PlayerMessage.REFRESH_CURRENT_POSITION) {
			PositionInfo info = (PositionInfo) data;
			String relTime = info.getRelTime();
			String duration = info.getTrackDuration();
			mCurrentTime.setText(relTime);
			long currentTime = ModelUtil.fromTimeString(relTime);
			long totalTime = ModelUtil.fromTimeString(duration);
			if (totalTime != 0) {
				int position = (int) (((float) currentTime / totalTime) * 100);
				mSeekBar.setProgress(position);
			}
		}else if(id == PlayerMessage.REFRESH_VOLUME){

		}
	}

	private void initView() {
		mBackground = findViewById(R.id.player_background);
		mToolbar = (Toolbar) findViewById(R.id.player_toolbar);
		setSupportActionBar(mToolbar);
		mTitle = (TextView) findViewById(R.id.player_title);
		mArtist = (TextView) findViewById(R.id.player_artist);
		mAlbum = (TextView) findViewById(R.id.player_album);
		mThumb = (SimpleDraweeView) findViewById(R.id.player_thumb);
		mCurrentTime = (TextView) findViewById(R.id.player_current_time);
		mTotalTime = (TextView) findViewById(R.id.player_total_time);
		mSeekBar = (SeekBar) findViewById(R.id.player_seekbar);

		mSeekBar.setOnSeekBarChangeListener(
				new SeekBar.OnSeekBarChangeListener() {
					@Override
					public void onProgressChanged(SeekBar seekBar, int progress,
							boolean fromUser) {

					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {

					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {

					}
				});

		mModeButton = (ImageView) findViewById(R.id.player_model);
		mModeButton.setOnClickListener(this);
		mPrevButton = (ImageView) findViewById(R.id.player_prev);
		mPrevButton.setOnClickListener(this);
		mPlayPauseButton = (ImageView) findViewById(R.id.player_play_pause);
		mPlayPauseButton.setOnClickListener(this);
		mNextButton = (ImageView) findViewById(R.id.player_next);
		mNextButton.setOnClickListener(this);
		mListButton = (ImageView) findViewById(R.id.player_playlist);
		mListButton.setOnClickListener(this);
	}

	private void generateBackgroundColor(Bitmap bitmap) {
		Palette palette = Palette.from(bitmap).generate();
		mBackgroundColor = palette.getDarkMutedColor(
				getResources().getColor(R.color.half_transparent));
	}

}
