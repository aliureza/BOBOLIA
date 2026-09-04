package com.example.robotvoice;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A lightweight drawn mascot with four visual states: idle, listening, speaking and dancing. */
public final class MascotView extends View {
    public enum State { IDLE, LISTENING, SPEAKING, DANCING }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private State state = State.IDLE;
    private float phase;
    private ValueAnimator animator;

    public MascotView(Context context) { super(context); init(); }
    public MascotView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public MascotView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        p.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> { phase = (float) a.getAnimatedValue() * 6.2831855f; invalidate(); });
    }

    public void setState(State newState) {
        if (newState == null) newState = State.IDLE;
        state = newState;
        if (newState == State.IDLE) {
            if (animator.isRunning()) animator.cancel();
            phase = 0f;
        } else if (!animator.isRunning()) {
            animator.start();
        }
        invalidate();
    }

    public State getState() { return state; }

    @Override protected void onDetachedFromWindow() {
        if (animator.isRunning()) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float s = Math.min(w, h) / 260f;

        float bob = state == State.SPEAKING ? (float)Math.sin(phase * 2) * 4f * s : 0f;
        float danceX = state == State.DANCING ? (float)Math.sin(phase) * 10f * s : 0f;
        float danceR = state == State.DANCING ? (float)Math.sin(phase) * 0.10f : 0f;
        c.save();
        c.translate(cx + danceX, cy + bob + (state == State.DANCING ? Math.abs((float)Math.sin(phase)) * -6f * s : 0f));
        c.rotate((float)Math.toDegrees(danceR));

        // soft shadow
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33000000);
        c.drawOval(new RectF(-88*s, 76*s, 88*s, 102*s), p);

        // antenna
        p.setStrokeWidth(7*s);
        p.setColor(0xFFE8F1FF);
        c.drawLine(0, -86*s, 0, -108*s, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF63D7FF);
        c.drawCircle(0, -114*s, 10*s, p);

        // body/head
        p.setColor(0xFF17304B);
        c.drawRoundRect(new RectF(-86*s, -72*s, 86*s, 82*s), 42*s, 42*s, p);
        p.setColor(0xFF294C6B);
        c.drawRoundRect(new RectF(-70*s, -57*s, 70*s, 58*s), 32*s, 32*s, p);

        // ears
        p.setColor(0xFF63D7FF);
        c.drawRoundRect(new RectF(-101*s, -18*s, -82*s, 24*s), 10*s, 10*s, p);
        c.drawRoundRect(new RectF(82*s, -18*s, 101*s, 24*s), 10*s, 10*s, p);

        // eyes
        float eyeY = -15*s;
        float eyeGap = 30*s;
        float eyeR = state == State.SPEAKING ? (6*s + (float)Math.abs(Math.sin(phase*2))*2*s) : 8*s;
        p.setColor(0xFFB9F3FF);
        c.drawCircle(-eyeGap, eyeY, eyeR, p);
        c.drawCircle(eyeGap, eyeY, eyeR, p);

        // mouth changes by state
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(7*s);
        p.setColor(0xFFB9F3FF);
        if (state == State.LISTENING) {
            c.drawOval(new RectF(-24*s, 18*s, 24*s, 46*s), p);
        } else if (state == State.SPEAKING) {
            float mouth = 12*s + (float)Math.abs(Math.sin(phase*2))*16*s;
            c.drawOval(new RectF(-22*s, 16*s, 22*s, 16*s + mouth), p);
        } else if (state == State.DANCING) {
            c.drawArc(new RectF(-28*s, 12*s, 28*s, 52*s), 10, 160, false, p);
        } else {
            c.drawArc(new RectF(-28*s, 10*s, 28*s, 46*s), 15, 150, false, p);
        }
        p.setStyle(Paint.Style.FILL);

        // state badge / sound waves
        if (state == State.LISTENING) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4*s);
            p.setColor(0xFF63D7FF);
            float wave = (float)(Math.abs(Math.sin(phase)) * 8*s);
            c.drawArc(new RectF(-112*s-wave, -42*s, -76*s-wave, 42*s), -65, 130, false, p);
            c.drawArc(new RectF(76*s+wave, -42*s, 112*s+wave, 42*s), -65, -130, false, p);
            p.setStyle(Paint.Style.FILL);
        } else if (state == State.SPEAKING) {
            p.setColor(0xFF63D7FF);
            float r = 8*s + (float)Math.abs(Math.sin(phase*2))*5*s;
            c.drawCircle(-105*s, 0, r, p);
            c.drawCircle(105*s, 0, r, p);
        } else if (state == State.DANCING) {
            p.setColor(0xFFFFD166);
            c.drawCircle(-112*s, -70*s, 7*s, p);
            c.drawCircle(112*s, -70*s, 7*s, p);
        }
        c.restore();
    }
}
