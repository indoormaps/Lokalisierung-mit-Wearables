/*
 * Copyright (C) 2008 ZXing authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package project.context.localization.glass.qr.barcode.scan.ui;

// Adjust to whatever the main package name is

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;

import java.util.ArrayList;
import java.util.List;

import google.zxing.client.android.camera.CameraManager;
import project.context.localization.glass.R;

//import com.google.zxing.client.android.camera.CameraManager;
//import com.jaxbot.glass.qrlens.R;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result
 * points.
 *
 * ---------------------------------------------------------------------------------------------
 * <p>The code was imported from the following repository</p> https://github.com/jaxbot/glass-qrlens.
 *
 * <p>Some changes that were made will be explained in the following section:</p>
 * <ul>
 *      <li>
 *          In -- {@link project.context.localization.glass.qr.barcode.scan.CaptureActivity#onResume()} --
 *          <p>Time out of QR Code Scanner extended</p>
 *          <p>from 15 seconds to 60 seconds</p>
 *
 *     </li>
 *     <li>
 *         In -- {@link project.context.localization.glass.qr.barcode.scan.CaptureActivity#handleDecode(Result, Bitmap, float)} --
 *         <p>Play Beep sound every time</p>
 *     </li>
 *     <li>
 *         In -- {@link project.context.localization.glass.qr.barcode.scan.CaptureActivity#handleDecodeInternally(Result, Bitmap)} --
 *         <p>Does not cancel timer.</p>
 *         <p>Gets Text value from Qr Code and lookup location description by place id</p>
 *         <p>Resets SurfaceView by calling onPause(),</p>
 *         <p>re-initialising the camera and calling onResume()</p>
 *     </li>
 *     <li>
 *         In -- {@link project.context.localization.glass.qr.barcode.scan.ui.ViewfinderView#ViewfinderView(Context, AttributeSet)} --
 *         <p>Added LinearLayout with textView</p>
 *     </li>
 *     <li>
 *         In -- {@link project.context.localization.glass.qr.barcode.scan.ui.ViewfinderView#onDraw(Canvas)} --
 *         <p>Display resultText in TextView, adjust textView width and add it to the layout</p>
 *     </li>
 *</ul>
 * ---------------------------------------------------------------------------------------------
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

    private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192,
            128, 64};
    private static final long ANIMATION_DELAY = 80L;
    private static final int CURRENT_POINT_OPACITY = 0xA0;
    private static final int MAX_RESULT_POINTS = 20;
    private static final int POINT_SIZE = 6;

    private CameraManager cameraManager;
    private final Paint paint;
    private Bitmap resultBitmap;
    private final int maskColor;
    private final int resultColor;
    private final int laserColor;
    private final int resultPointColor;
    private final int scannerAlpha;
    private final List<ResultPoint> possibleResultPoints;
    private final List<ResultPoint> lastPossibleResultPoints;
    public String resultText;

    private final LinearLayout layout;
    private final TextView textView;
    private final DisplayMetrics mDisplayMetrics;

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.viewfinder_mask);
        resultColor = resources.getColor(R.color.result_view);
        laserColor = resources.getColor(R.color.viewfinder_laser);
        resultPointColor = resources.getColor(R.color.possible_result_points);
        scannerAlpha = 0;
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = null;

        layout = new LinearLayout(context);
        textView = new TextView(context);

        mDisplayMetrics = context.getResources().getDisplayMetrics();
    }

    public void setCameraManager(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }

    @Override
    public void onDraw(Canvas canvas) {
        Log.w("VIEWFINDER", "onDraw called");
        if (cameraManager == null) {
            return; // not ready yet, early draw before done configuring
        }
        Rect frame = cameraManager.getFramingRect();
        Rect previewFrame = cameraManager.getFramingRectInPreview();
        if (frame == null || previewFrame == null) {
            return;
        }
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        paint.setTextSize(24);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        canvas.drawText("Hover over QR code to scan", 320, 317, paint);
        canvas.drawLine(frame.left + 80, frame.top - 20, frame.left + 80, frame.top - 5, paint);
        canvas.drawLine(frame.left + 80, frame.top - 20, frame.left + 95, frame.top - 20, paint);
        canvas.drawLine(frame.right - 80, frame.top - 20, frame.right - 80, frame.top - 5, paint);
        canvas.drawLine(frame.right - 80, frame.top - 20, frame.right - 95, frame.top - 20, paint);
        canvas.drawLine(frame.right - 80, frame.bottom - 20, frame.right - 95, frame.bottom - 20, paint);
        canvas.drawLine(frame.left + 80, frame.bottom - 20, frame.left + 95, frame.bottom - 20, paint);
        canvas.drawLine(frame.right - 80, frame.bottom - 20, frame.right - 80, frame.bottom - 35, paint);
        canvas.drawLine(frame.left + 80, frame.bottom - 20, frame.left + 80, frame.bottom - 35, paint);

        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap(resultBitmap, null, frame, paint);
        }

        if (resultText != null) {

            paint.setAlpha(CURRENT_POINT_OPACITY);
            paint.setTextSize(24);
//            canvas.drawText(resultText, 250, 65, paint);

            textView.setVisibility(View.VISIBLE);
            textView.setText(resultText);
            textView.setTextColor(Color.parseColor("#FFFFFF"));



            textView.setMaxWidth(mDisplayMetrics.widthPixels);
            textView.setMaxWidth((getContext().getResources().getDisplayMetrics()).widthPixels);



            layout.removeAllViews();
            layout.addView(textView);
            layout.measure(canvas.getWidth(), canvas.getHeight());
            layout.layout(0, 0, canvas.getWidth(), canvas.getHeight());
//            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams)layout.getLayoutParams();
//
//            p.leftMargin = 200; // in PX
//            p.topMargin = 65; // in PX
//            textView.set(p);


        }


// To place the text view somewhere specific:
//canvas.translate(0, 0);

        layout.draw(canvas);
    }


    public void drawViewfinder() {
        Bitmap resultBitmap = this.resultBitmap;
        this.resultBitmap = null;
        if (resultBitmap != null) {
            resultBitmap.recycle();
        }
        invalidate();
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live
     * scanning display.
     *
     * @param barcode An image of the decoded barcode.
     */
    public void drawResultBitmap(Bitmap barcode) {
        resultBitmap = barcode;
        invalidate();
    }

    private void addPossibleResultPoint(ResultPoint point) {
        List<ResultPoint> points = possibleResultPoints;
        synchronized (points) {
            points.add(point);
            int size = points.size();
            if (size > MAX_RESULT_POINTS) {
                // trim it
                points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
            }
        }
    }

    public static final class ViewfinderResultPointCallback implements
            ResultPointCallback {

        private final ViewfinderView viewfinderView;

        public ViewfinderResultPointCallback(ViewfinderView viewfinderView) {
            this.viewfinderView = viewfinderView;
        }

        @Override
        public void foundPossibleResultPoint(ResultPoint point) {
            viewfinderView.addPossibleResultPoint(point);
        }
    }
}
