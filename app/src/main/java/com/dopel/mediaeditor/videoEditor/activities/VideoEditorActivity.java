package com.dopel.mediaeditor.videoEditor.activities;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.dopel.mediaeditor.R;
import com.dopel.mediaeditor.videoEditor.helpers.BackgroundExecutor;
import com.dopel.mediaeditor.videoEditor.helpers.Helper;
import com.dopel.mediaeditor.videoEditor.helpers.UiThreadExecutor;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.FFmpegExecuteResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpegLoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

public class VideoEditorActivity extends AppCompatActivity {

    private static final String KEY_VIDEO_URI = "VIDEO_URI";

    private static final String KEY_SB_VIDEO = "SB_VIDEO";
    private static final String KEY_SB_START = "SB_START";
    private static final String KEY_SB_END = "SB_END";

    VideoView videoView;
    Button btnPlay;
    Button btnTrim;
    SeekBar sbVideo;
    SeekBar sbStart;
    SeekBar sbEnd;
    TextView tvStart;
    TextView tvEnd;
    LinearLayout timelineView;

    String videoPath;
    String destinationPath;

    FFmpeg ffmpegInstance;

    public static void launch(Context context, Uri videoUri) {
        Intent i = new Intent(context, VideoEditorActivity.class);
        i.putExtra(KEY_VIDEO_URI, videoUri);
        context.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_editor);
        initviews();
        setupClicks();
        getIntentExtras();
    }

    private void setupClicks() {
        btnPlay.setOnClickListener(onPlayClick());
        btnTrim.setOnClickListener(onTrimClick());
    }

    private View.OnClickListener onTrimClick() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadFFmpegLib();
            }
        };
    }

    private View.OnClickListener onPlayClick() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoView.isPlaying()) {
                    videoView.pause();
                    btnPlay.setText("PLAY");
                } else {
                    videoView.start();
                    btnPlay.setText("PAUSE");
                }
            }
        };
    }

    private void initviews() {
        videoView = findViewById(R.id.videoView);
        btnPlay = findViewById(R.id.btnPlay);
        sbVideo = findViewById(R.id.sbVideo);
        sbStart = findViewById(R.id.sbStart);
        sbEnd = findViewById(R.id.sbEnd);
        tvStart = findViewById(R.id.tvStart);
        tvEnd = findViewById(R.id.tvEnd);
        btnTrim = findViewById(R.id.btnTrim);
        timelineView = findViewById(R.id.timelineView);
    }

    private void getIntentExtras() {
        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.containsKey(KEY_VIDEO_URI)) {
            Uri fileUri = (Uri) extras.get(KEY_VIDEO_URI);
            videoPath = Helper.getInstance().getVideoFilePath(getContentResolver(), fileUri, null);
            if (videoPath != null && !videoPath.isEmpty()) {
                populateViews();
            }
        }
    }

    private void populateViews() {
        videoView.setVideoPath(videoPath);
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                int totalDuration = videoView.getDuration() / 1000;
                sbVideo.setMax(totalDuration);
                sbStart.setMax(totalDuration);
                sbEnd.setMax(totalDuration);
                sbEnd.setProgress(videoView.getDuration() * 1000);
                tvEnd.setText(String.valueOf(Helper.getInstance().createTimeFromMS(videoView.getDuration())));
                sbVideo.setOnSeekBarChangeListener(onSeekbarVideoChanged(KEY_SB_VIDEO));
                sbStart.setOnSeekBarChangeListener(onSeekbarVideoChanged(KEY_SB_START));
                sbEnd.setOnSeekBarChangeListener(onSeekbarVideoChanged(KEY_SB_END));
                handlePlaybackSeekbar();
                populateTimeline(sbStart.getWidth());
            }
        });
    }

    private void loadFFmpegLib() {
        if (ffmpegInstance == null) {
            ffmpegInstance = FFmpeg.getInstance(this);
        }
        try {
            ffmpegInstance.loadBinary(new FFmpegLoadBinaryResponseHandler() {
                @Override
                public void onFailure() {

                }

                @Override
                public void onSuccess() {
                    startCropping();
                }

                @Override
                public void onStart() {

                }

                @Override
                public void onFinish() {

                }
            });
        } catch (FFmpegNotSupportedException e) {
            e.printStackTrace();
        }
    }

    private void handlePlaybackSeekbar() {
        final Handler mHandler = new Handler();
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (videoView != null) {
                    int mCurrentPosition = videoView.getCurrentPosition() / 1000;
                    sbVideo.setProgress(mCurrentPosition);
                }
                mHandler.postDelayed(this, 1000);
            }
        });
    }

    private SeekBar.OnSeekBarChangeListener onSeekbarVideoChanged(final String sbType) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (sbType.equals(KEY_SB_VIDEO) || sbType.equals(KEY_SB_START)) {
                        videoView.seekTo(seekBar.getProgress() * 1000);
                    }
                    if (sbType.equals(KEY_SB_START)) {
                        tvStart.setText(Helper.getInstance().createTimeFromMS(seekBar.getProgress() * 1000));
                        if (progress >= (sbEnd.getProgress() - 10)) {
                            sbEnd.setProgress(progress + 10);
                            tvEnd.setText(Helper.getInstance().createTimeFromMS(sbEnd.getProgress() * 1000));
                        }
                    } else if (sbType.equals(KEY_SB_END)) {
                        sbEnd.setProgress(progress);
                        if (progress <= (sbStart.getProgress() + 10)) {
                            sbStart.setProgress(progress - 10);
                            tvStart.setText(Helper.getInstance().createTimeFromMS(sbStart.getProgress() * 1000));
                            tvEnd.setText(Helper.getInstance().createTimeFromMS(progress * 1000));
                        }
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        };
    }

    private void startCropping() {
        destinationPath = Helper.getInstance().getVideoFilePath();
        String startPoint = Helper.getInstance().createTimeFromMS(sbStart.getProgress() * 1000);
        String endPoint = Helper.getInstance().createTimeFromMS(sbEnd.getProgress() * 1000);
        executeCommand(Helper.getInstance().getCropCommand(videoPath, destinationPath, startPoint, endPoint));
    }

    private void executeCommand(String[] com) {
        final ProgressDialog pdialog = new ProgressDialog(this);
        pdialog.setMessage("Processing");
        try {
            ffmpegInstance.execute(com, new FFmpegExecuteResponseHandler() {
                @Override
                public void onSuccess(String message) {
                }

                @Override
                public void onProgress(String message) {
                    Log.d("PROGRESS", "Progress:" + message);

                }

                @Override
                public void onFailure(String message) {
                }

                @Override
                public void onStart() {
                    pdialog.show();
                }

                @Override
                public void onFinish() {
                    pdialog.dismiss();
                    Toast.makeText(VideoEditorActivity.this, "finished", Toast.LENGTH_SHORT).show();
                    setupPrevPlayer();
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            e.printStackTrace();
        }
    }

    private void setupPrevPlayer() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_player_prev, null);
        final VideoView videoView = view.findViewById(R.id.videoView);
        final Button btnPlay = view.findViewById(R.id.btnPlay);
        final SeekBar sbVideo = view.findViewById(R.id.sbVideo);
        dialog.setView(view);
        videoView.setVideoPath(destinationPath);
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                sbVideo.setMax(videoView.getDuration() / 1000);
                btnPlay.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (videoView.isPlaying()) {
                            videoView.pause();
                            btnPlay.setText("PLAY");
                        } else {
                            videoView.start();
                            btnPlay.setText("PAUSE");
                        }
                    }
                });
                sbVideo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser)
                            videoView.seekTo(progress * 1000);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {

                    }
                });
                final Handler mHandler = new Handler();
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        if (videoView != null) {
                            int mCurrentPosition = videoView.getCurrentPosition() / 1000;
                            sbVideo.setProgress(mCurrentPosition);
                        }
                        mHandler.postDelayed(this, 1000);
                    }
                });

            }
        });
        dialog.show();
    }

    private void populateTimeline(final int viewWidth) {
        BackgroundExecutor.execute(new BackgroundExecutor.Task("", 0L, "") {
                                       @Override
                                       public void execute() {
                                           try {
                                               LongSparseArray<Bitmap> thumbnailList = new LongSparseArray<>();

                                               MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                                               mediaMetadataRetriever.setDataSource(VideoEditorActivity.this, Uri.parse(videoPath));

                                               // Retrieve media data
                                               long videoLengthInMs = Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;

                                               // Set thumbnail properties (Thumbs are squares)
                                               final int thumbWidth = 80;
                                               final int thumbHeight = 200;

                                               int numThumbs = (int) Math.ceil(((float) viewWidth) / thumbWidth);

                                               final long interval = videoLengthInMs / numThumbs;

                                               for (int i = 0; i < numThumbs; ++i) {
                                                   Bitmap bitmap = mediaMetadataRetriever.getFrameAtTime(i * interval, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                                                   // TODO: bitmap might be null here, hence throwing NullPointerException. You were right
                                                   try {
                                                       bitmap = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, false);
                                                   } catch (Exception e) {
                                                       e.printStackTrace();
                                                   }
                                                   thumbnailList.put(i, bitmap);
                                               }

                                               mediaMetadataRetriever.release();
                                               returnBitmaps(thumbnailList);
                                           } catch (final Throwable e) {
                                               Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
                                           }
                                       }
                                   }
        );
    }

    private void returnBitmaps(final LongSparseArray<Bitmap> thumbnailList) {
        UiThreadExecutor.runTask("", new Runnable() {
                    @Override
                    public void run() {
                        LongSparseArray<Bitmap> mBitmapList = thumbnailList;
                        Log.d("", "");
                        inflateBmps(mBitmapList);
                    }
                }
                , 0L);
    }

    private void inflateBmps(LongSparseArray<Bitmap> mBitmapList) {
        ImageView imageView;
        for (int i = 0; i < mBitmapList.size(); i++) {
            imageView = new ImageView(this);
            imageView.setImageBitmap(mBitmapList.get(i));
            timelineView.addView(imageView);
        }
    }
}
