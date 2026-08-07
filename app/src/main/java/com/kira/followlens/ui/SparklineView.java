package com.kira.followlens.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.kira.followlens.R;

/**
 * A single-series trend line.
 *
 * One series, so there is no legend — the label beside it names the measure. The
 * line is 2px and the fill is a faint wash, keeping the mark thin and the ink
 * recessive; the geometry lives in {@link SparklineGeometry} so it can be tested.
 */
public class SparklineView extends View {

    private static final float STROKE_DP = 2f;
    private static final float DOT_RADIUS_DP = 3.5f;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private int[] values = new int[0];

    public SparklineView(Context context) {
        this(context, null);
    }

    public SparklineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(STROKE_DP * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(ContextCompat.getColor(context, R.color.accent));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(ContextCompat.getColor(context, R.color.accent_dim));

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(ContextCompat.getColor(context, R.color.accent));
    }

    /** Values in chronological order, oldest first. */
    public void setValues(int[] chronological) {
        this.values = chronological == null ? new int[0] : chronological;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values.length == 0) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        float inset = STROKE_DP * density;           // keep the stroke inside bounds
        float width = getWidth() - inset * 2f;
        float height = getHeight() - inset * 2f;
        if (width <= 0 || height <= 0) {
            return;
        }

        float[] xs = SparklineGeometry.xs(values.length, width);
        float[] ys = SparklineGeometry.ys(values, height);

        // A lone scan has no line to draw, so mark the point instead.
        if (values.length == 1) {
            canvas.drawCircle(inset + xs[0] + DOT_RADIUS_DP * density, inset + ys[0],
                    DOT_RADIUS_DP * density, dotPaint);
            return;
        }

        linePath.reset();
        fillPath.reset();
        for (int i = 0; i < values.length; i++) {
            float x = inset + xs[i];
            float y = inset + ys[i];
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, getHeight());
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        fillPath.lineTo(inset + xs[values.length - 1], getHeight());
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        // Only the newest point is marked: a dot on every point turns a trend
        // line into noise.
        canvas.drawCircle(inset + xs[values.length - 1], inset + ys[values.length - 1],
                DOT_RADIUS_DP * density, dotPaint);
    }
}
