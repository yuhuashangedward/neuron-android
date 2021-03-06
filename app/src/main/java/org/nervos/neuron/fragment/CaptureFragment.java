package org.nervos.neuron.fragment;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.client.result.ResultParser;
import com.uuzuche.lib_zxing.camera.CameraManager;
import com.uuzuche.lib_zxing.decoding.InactivityTimer;
import com.uuzuche.lib_zxing.view.ViewfinderView;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.Permission;

import org.nervos.neuron.R;
import org.nervos.neuron.view.TitleBar;
import org.nervos.neuron.util.BitmapUtils;
import org.nervos.neuron.util.PickPic;
import org.nervos.neuron.util.qrcode.BitmapDecoder;
import org.nervos.neuron.util.qrcode.CaptureActivityHandler;
import org.nervos.neuron.util.qrcode.CodeUtils;
import org.nervos.neuron.util.ScreenUtils;
import org.nervos.neuron.util.permission.PermissionUtil;
import org.nervos.neuron.util.permission.RuntimeRationale;

import java.io.IOException;
import java.util.Vector;

import static android.app.Activity.RESULT_OK;

/**
 * 自定义实现的扫描Fragment
 */
public class CaptureFragment extends NBaseFragment implements SurfaceHolder.Callback {

    private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
    private boolean hasSurface;
    private Vector<BarcodeFormat> decodeFormats;
    private String characterSet;
    private InactivityTimer inactivityTimer;
    private MediaPlayer mediaPlayer;
    private boolean playBeep;
    private static final float BEEP_VOLUME = 0.10f;
    private boolean vibrate;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private CodeUtils.AnalyzeCallback analyzeCallback;
    private Camera camera;
    private TitleBar titleBar;
    private boolean isShowRight = true;

    @Override
    protected int getContentLayout() {
        return R.layout.fragment_capture;
    }

    @Override
    protected void initView() {
        super.initView();
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        surfaceHolder = surfaceView.getHolder();
        titleBar = (TitleBar) findViewById(R.id.title);
        if (!isShowRight)
            titleBar.hideRight();
    }

    @Override
    protected void initData() {
        super.initData();
        CameraManager.init(getActivity().getApplication());
        hasSurface = false;
        inactivityTimer = new InactivityTimer(this.getActivity());

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) titleBar.getLayoutParams();
        params.topMargin = ScreenUtils.getStatusHeight(getActivity());
        titleBar.setLayoutParams(params);
    }

    @Override
    protected void initAction() {
        super.initAction();
        titleBar.setOnRightClickListener(() -> {
            AndPermission.with(getActivity())
                    .runtime().permission(Permission.Group.STORAGE)
                    .rationale(new RuntimeRationale())
                    .onGranted(permissions -> {
                        Intent openAlbumIntent = new Intent(Intent.ACTION_GET_CONTENT);
                        openAlbumIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
                        startActivityForResult(openAlbumIntent, 1);
                    })
                    .onDenied(permissions -> {
                        dismissProgressBar();
                        PermissionUtil.showSettingDialog(getActivity(), permissions);
                    })
                    .start();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        decodeFormats = null;
        characterSet = null;

        playBeep = true;
        AudioManager audioService = (AudioManager) getActivity().getSystemService(getActivity().AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            playBeep = false;
        }
        initBeepSound();
        vibrate = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        CameraManager.get().closeDriver();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        inactivityTimer.shutdown();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data.getData() != null) {
            String path = PickPic.getPath(getActivity().getApplicationContext(), data.getData());
            if (!TextUtils.isEmpty(path)) {
                Bitmap img = BitmapUtils.getCompressedBitmap(path);
                BitmapDecoder decoder = new BitmapDecoder(getActivity());
                Result result = decoder.getRawResult(img);
                if (result != null) {
                    String qr = ResultParser.parseResult(result).toString();
                    analyzeCallback.onAnalyzeSuccess(img, qr);
                } else {
                    Toast.makeText(getActivity(), R.string.qr_photo_failed, Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getActivity(), R.string.qr_photo_failed, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Handler scan result
     *
     * @param result
     * @param barcode
     */
    public void handleDecode(Result result, Bitmap barcode) {
        inactivityTimer.onActivity();
        playBeepSoundAndVibrate();

        if (result == null || TextUtils.isEmpty(result.getText())) {
            if (analyzeCallback != null) {
                analyzeCallback.onAnalyzeFailed();
            }
        } else {
            if (analyzeCallback != null) {
                analyzeCallback.onAnalyzeSuccess(barcode, result.getText());
            }
        }
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            CameraManager.get().openDriver(surfaceHolder);
            camera = CameraManager.get().getCamera();
        } catch (Exception e) {
            if (callBack != null) {
                callBack.callBack(e);
            }
            return;
        }
        if (callBack != null) {
            callBack.callBack(null);
        }
        if (handler == null) {
            handler = new CaptureActivityHandler(this, decodeFormats, characterSet, viewfinderView);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
        if (camera != null) {
            if (camera != null && CameraManager.get().isPreviewing()) {
                if (!CameraManager.get().isUseOneShotPreviewCallback()) {
                    camera.setPreviewCallback(null);
                }
                camera.stopPreview();
                CameraManager.get().getPreviewCallback().setHandler(null, 0);
                CameraManager.get().getAutoFocusCallback().setHandler(null, 0);
                CameraManager.get().setPreviewing(false);
            }
        }
    }

    public Handler getHandler() {
        return handler;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();

    }

    private void initBeepSound() {
        if (playBeep && mediaPlayer == null) {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it
            // too loud,
            // so we now play on the music stream.
            getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(beepListener);

            AssetFileDescriptor file = getResources().openRawResourceFd(
                    R.raw.beep);
            try {
                mediaPlayer.setDataSource(file.getFileDescriptor(),
                        file.getStartOffset(), file.getLength());
                file.close();
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mediaPlayer.prepare();
            } catch (IOException e) {
                mediaPlayer = null;
            }
        }
    }

    public void setRight(boolean show) {
        isShowRight = show;
    }

    private static final long VIBRATE_DURATION = 200L;

    private void playBeepSoundAndVibrate() {
        if (playBeep && mediaPlayer != null) {
            mediaPlayer.start();
        }
        if (vibrate) {
            Vibrator vibrator = (Vibrator) getActivity().getSystemService(getActivity().VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }

    /**
     * When the beep has finished playing, rewind to queue up another one.
     */
    private final MediaPlayer.OnCompletionListener beepListener = new MediaPlayer.OnCompletionListener() {
        public void onCompletion(MediaPlayer mediaPlayer) {
            mediaPlayer.seekTo(0);
        }
    };

    public CodeUtils.AnalyzeCallback getAnalyzeCallback() {
        return analyzeCallback;
    }

    public void setAnalyzeCallback(CodeUtils.AnalyzeCallback analyzeCallback) {
        this.analyzeCallback = analyzeCallback;
    }

    @Nullable
    CameraInitCallBack callBack;

    /**
     * Set callback for Camera check whether Camera init success or not.
     */
    public void setCameraInitCallBack(CameraInitCallBack callBack) {
        this.callBack = callBack;
    }

    interface CameraInitCallBack {
        /**
         * Callback for Camera init result.
         *
         * @param e If is's null,means success.otherwise Camera init failed with the Exception.
         */
        void callBack(Exception e);
    }


}
