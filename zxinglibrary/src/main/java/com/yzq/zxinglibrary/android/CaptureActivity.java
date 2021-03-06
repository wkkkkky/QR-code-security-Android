package com.yzq.zxinglibrary.android;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;
import com.yzq.zxinglibrary.R;
import com.yzq.zxinglibrary.bean.ZxingConfig;
import com.yzq.zxinglibrary.camera.CameraManager;
import com.yzq.zxinglibrary.common.Constant;
import com.yzq.zxinglibrary.decode.BitmapLuminanceSource;
import com.yzq.zxinglibrary.decode.DecodeImgCallback;
import com.yzq.zxinglibrary.decode.DecodeImgThread;
import com.yzq.zxinglibrary.decode.ImageUtil;
import com.yzq.zxinglibrary.view.ViewfinderView;

import java.io.IOException;
import java.util.Map;

import static com.google.zxing.ResultPoint.orderBestPatterns;


/**
 * @author: yzq
 * @date: 2017/10/26 15:22
 * @declare :扫一扫
 */

public class CaptureActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnClickListener {

    private static final String TAG = CaptureActivity.class.getSimpleName();
    public ZxingConfig config;
    private SurfaceView previewView;
    private ViewfinderView viewfinderView;
    private AppCompatImageView flashLightIv;
    private TextView flashLightTv;
    private AppCompatImageView backIv;
    private LinearLayoutCompat flashLightLayout;
    private LinearLayoutCompat albumLayout;
    private LinearLayoutCompat bottomLayout;
    private boolean hasSurface;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;
    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private SurfaceHolder surfaceHolder;
    public static class ImageBinder extends Binder {
        private Bitmap bitmap;

        public ImageBinder(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        public Bitmap getBitmap() {
            return bitmap;
        }
    }


    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }


    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 保持Activity处于唤醒状态,让屏幕保持恒亮
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.BLACK);
        }

        /*先获取配置信息*/
        try {
            config = (ZxingConfig) getIntent().getExtras().get(Constant.INTENT_ZXING_CONFIG);
        } catch (Exception e) {

            Log.i("config", e.toString());
        }

        if (config == null) {
            config = new ZxingConfig();
        }

        //将指定的资源xml文件加载到对应的activity中
        setContentView(R.layout.activity_capture);


        initView();

        hasSurface = false;

        inactivityTimer = new InactivityTimer(this);

        //BeepManager是负责：在二维码解码成功时 播放“bee”的声音，同时还可以震动。
        beepManager = new BeepManager(this);
        beepManager.setPlayBeep(config.isPlayBeep());
        beepManager.setVibrate(config.isShake());


    }


    private void initView() {
        previewView = findViewById(R.id.preview_view);
        previewView.setOnClickListener(this);

        viewfinderView = findViewById(R.id.viewfinder_view);
        viewfinderView.setZxingConfig(config);


        backIv = findViewById(R.id.backIv);
        backIv.setOnClickListener(this);

        flashLightIv = findViewById(R.id.flashLightIv);
        flashLightTv = findViewById(R.id.flashLightTv);

        flashLightLayout = findViewById(R.id.flashLightLayout);
        flashLightLayout.setOnClickListener(this);
        albumLayout = findViewById(R.id.albumLayout);
        albumLayout.setOnClickListener(this);
        bottomLayout = findViewById(R.id.bottomLayout);

        //设置组件的显示
        switchVisibility(bottomLayout, config.isShowbottomLayout());
        switchVisibility(flashLightLayout, config.isShowFlashLight());
        switchVisibility(albumLayout, config.isShowAlbum());


        /*有闪光灯就显示手电筒按钮  否则不显示*/
        if (isSupportCameraLedFlash(getPackageManager())) {
            flashLightLayout.setVisibility(View.VISIBLE);
        } else {
            flashLightLayout.setVisibility(View.GONE);
        }
    }


    /**
     * @param pm
     * @return 是否有闪光灯
     */
    public static boolean isSupportCameraLedFlash(PackageManager pm) {
        if (pm != null) {
            FeatureInfo[] features = pm.getSystemAvailableFeatures();
            if (features != null) {
                for (FeatureInfo f : features) {
                    if (f != null && PackageManager.FEATURE_CAMERA_FLASH.equals(f.name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * @param flashState 切换闪光灯图片
     */
    public void switchFlashImg(int flashState) {

        if (flashState == Constant.FLASH_OPEN) {
            flashLightIv.setImageResource(R.drawable.ic_open);
            flashLightTv.setText(R.string.close_flash);
        } else {
            flashLightIv.setImageResource(R.drawable.ic_close);
            flashLightTv.setText(R.string.open_flash);
        }

    }

    /**
     * @param rawResult 返回的扫描结果
     */
    public void handleDecode(Result rawResult,String path) {

        inactivityTimer.onActivity();
        beepManager.playBeepSoundAndVibrate();
        Intent intent = getIntent();
        intent.putExtra(Constant.CODED_CONTENT, rawResult.getText());
        intent.putExtra(Constant.IMG_PATH,path);
        Log.i("handledecode扫描结果：", rawResult.getText());
        setResult(RESULT_OK, intent);
        this.finish();
    }
    public void handleDecode(Result rawResult) {

        inactivityTimer.onActivity();
        beepManager.playBeepSoundAndVibrate();
        Intent intent = getIntent();
        intent.putExtra(Constant.CODED_CONTENT, rawResult.getText());
        Log.i("handledecode扫描结果：", rawResult.getText());
        setResult(RESULT_OK, intent);
        this.finish();
    }
    /**
     * 旋转bitmap
     * @return
     */
    private Bitmap rotateBitmap(Bitmap origin) {
        if (origin == null) {
            return null;
        }
        float alpha = 90;
        int width = origin.getWidth();
        int height = origin.getHeight();
        Matrix matrix = new Matrix();
        matrix.setRotate(alpha);
        // 围绕原地进行旋转
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        if (newBM.equals(origin)) {
            return newBM;
        }
        origin.recycle();
        return newBM;
    }

    /**
     * 提取二维码中logo部分
     * @return
     */
    public Bitmap get_qrcode(Bitmap bitmap,Result rawResult) throws NotFoundException {
        // 定位点的坐标，按照左下、左上、右上顺序
        MultiFormatReader multiFormatReader = new MultiFormatReader();
        try {
            // 重新获取结果，这里使用传过来的rawresult，是经过了其他处理的，其中的定位点坐标有所改变，需要重新获取结果
            rawResult = multiFormatReader.decodeWithState(new BinaryBitmap(new HybridBinarizer(new BitmapLuminanceSource(bitmap))));
            Log.i("LOGO解析结果", rawResult.getText());
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("解析的图片结果","失败");
        }
        Map<ResultMetadataType, Object> metadate = rawResult.getResultMetadata();
        String ERROR_CODE = String.valueOf(metadate.get(ResultMetadataType.ERROR_CORRECTION_LEVEL));
        if (ERROR_CODE != null){
            double logo_range = 1;
            switch (ERROR_CODE){
                case "L":
                    logo_range = (double) 0.07;
                case "M":
                    logo_range = (double) 0.15;
                case "Q":
                    logo_range = (double) 0.25;
                case "H":
                    logo_range = (double) 0.30;
            }
            System.out.println("Result定位"+ ERROR_CODE+logo_range);
            ResultPoint[] resultPoint = rawResult.getResultPoints();
            orderBestPatterns(resultPoint);
            float x1 = resultPoint[0].getX();
            float y1 = resultPoint[0].getY();
            float x2 = resultPoint[1].getX();
            float y2 = resultPoint[1].getY();
            float x3 = resultPoint[2].getX();
            float y3 = resultPoint[2].getY();
            System.out.println("Result定位"+ x1+" "+y1+" "+ x2+" "+y2+" "+ x3+" "+y3);
            // 定位点与起始点的差值
            int deviate = 25;
            // 计算二维码图片边长
            int length = (int) Math.sqrt(Math.abs(x1 - x2) * Math.abs(x1 - x2) + Math.abs(y1 - y2) * Math.abs(y1 - y2));
            int mid = (int) (length*((1-Math.sqrt(logo_range))/2));
            int new_len = (int) (length*Math.sqrt(logo_range));
            // 根据二维码定位坐标计算起始坐标
            int x = (int) (Math.round(x3)+mid);
            int y = (int) (Math.round(y3)+mid);
            System.out.println("Result定位"+ x+" "+y+" "+ mid);
            try{
                Bitmap new_bmp = Bitmap.createBitmap(bitmap,x,y, new_len, new_len);
                return new_bmp;
            }catch (Exception e){
                return bitmap;
            }
        }
        else{
            Log.i("GET_LOGOERROR","NO ERROR_CODE");
        }
        return bitmap;
    }

    public static Bitmap get_qrcode(Bitmap bitmap) throws NotFoundException {
        // 定位点的坐标，按照左下、左上、右上顺序
        MultiFormatReader multiFormatReader = new MultiFormatReader();
        try {
            // 重新获取结果，这里使用传过来的rawresult，是经过了其他处理的，其中的定位点坐标有所改变，需要重新获取结果
            Result rawResult = multiFormatReader.decodeWithState(new BinaryBitmap(new HybridBinarizer(new BitmapLuminanceSource(bitmap))));
            Log.i("LOGO解析结果", rawResult.getText());
            Map<ResultMetadataType, Object> metadate = rawResult.getResultMetadata();
            String ERROR_CODE = String.valueOf(metadate.get(ResultMetadataType.ERROR_CORRECTION_LEVEL));
            if (ERROR_CODE != null){
                double logo_range = 1;
                switch (ERROR_CODE){
                    case "L":
                        logo_range = (double) 0.07;
                    case "M":
                        logo_range = (double) 0.15;
                    case "Q":
                        logo_range = (double) 0.25;
                    case "H":
                        logo_range = (double) 0.30;
                }
                System.out.println("Result定位"+ ERROR_CODE+logo_range);
                ResultPoint[] resultPoint = rawResult.getResultPoints();
                orderBestPatterns(resultPoint);
                float x1 = resultPoint[0].getX();
                float y1 = resultPoint[0].getY();
                float x2 = resultPoint[1].getX();
                float y2 = resultPoint[1].getY();
                float x3 = resultPoint[2].getX();
                float y3 = resultPoint[2].getY();
                System.out.println("Result定位"+ x1+" "+y1+" "+ x2+" "+y2+" "+ x3+" "+y3);
                // 计算二维码图片边长
                int length = (int) Math.sqrt(Math.abs(x1 - x2) * Math.abs(x1 - x2) + Math.abs(y1 - y2) * Math.abs(y1 - y2));
                int mid = (int) (length*((1-Math.sqrt(logo_range))/2));
                int new_len = (int) (length*Math.sqrt(logo_range));
                // 根据二维码定位坐标计算起始坐标
                int x = (int) (Math.round(x2)+mid);
                int y = (int) (Math.round(y2)+mid);
                System.out.println("Result定位"+ x+" "+y+" "+ mid);
                try{
                    Bitmap new_bmp = Bitmap.createBitmap(bitmap,x,y, new_len, new_len);
                    return new_bmp;
                }catch (Exception e){
                    Log.i("GET_QRCODE失败","返回原来数据");
                    return bitmap;
                }
            }
            else{
                Log.i("GET_LOGOERROR","NO ERROR_CODE");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("解析的图片结果","失败");
        }
        return bitmap;
    }

    /**
    * 将截取logo传输到MainActivity
    */
    public void get_logo(Bitmap bitmap,Result rawResult) throws NotFoundException {
        if (bitmap == null){
            Log.i("Bitmap测试","get_log没bitmap");
        }
        bitmap = get_qrcode(bitmap,rawResult);
        Intent intent = getIntent();
        Bundle bundle = new Bundle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            bundle.putBinder("bitmap", new ImageBinder(bitmap));
        }
        intent.putExtras(bundle);
    }

    private void switchVisibility(View view, boolean b) {
        if (b) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        cameraManager = new CameraManager(getApplication(), config);

        viewfinderView.setCameraManager(cameraManager);
        handler = null;

        //得到 SurfaceHolder对象
        surfaceHolder = previewView.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);
        }
        else {
            // 重置callback，等待surfaceCreated()来初始化camera
            surfaceHolder.addCallback(this);
        }

        beepManager.updatePrefs();
        inactivityTimer.onResume();

    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            return;
        }
        try {
            // 打开Camera硬件设备
            cameraManager.openDriver(surfaceHolder);
            // 创建一个handler来打开预览，并抛出一个运行时异常
            if (handler == null) {
                handler = new CaptureActivityHandler(this, cameraManager);
            }
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    private void displayFrameworkBugMessageAndExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("扫一扫");
        builder.setMessage(getString(R.string.msg_camera_framework_bug));
        builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
        builder.setOnCancelListener(new FinishListener(this));
        builder.show();
    }

    @Override
    protected void onPause() {

        Log.i("CaptureActivity","onPause");
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        beepManager.close();
        cameraManager.closeDriver();

        if (!hasSurface) {

            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        viewfinderView.stopAnimator();
        super.onDestroy();
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
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    @Override
    public void onClick(View view) {

        int id = view.getId();
        if (id == R.id.flashLightLayout) {
            /*切换闪光灯*/
            cameraManager.switchFlashLight(handler);
        } else if (id == R.id.albumLayout) {
            /*打开相册*/
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, Constant.REQUEST_IMAGE);
        } else if (id == R.id.backIv) {
            finish();
        }


    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Constant.REQUEST_IMAGE && resultCode == RESULT_OK) {
            final String path = ImageUtil.getImageAbsolutePath(this, data.getData());


            new DecodeImgThread(path, new DecodeImgCallback() {
                @Override
                public void onImageDecodeSuccess(Result result) {
                    Log.i("相册扫描结果", String.valueOf(result));
                    handleDecode(result,path);

                }

                @Override
                public void onImageDecodeFailed() {
                    Toast.makeText(CaptureActivity.this, R.string.scan_failed_tip, Toast.LENGTH_SHORT).show();
                }
            }).run();
        }
    }
}
