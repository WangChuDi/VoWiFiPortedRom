// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Material 3 color roles and shapes using platform widgets, without AndroidX. */
final class MaterialTheme {
    final boolean dark;
    final int background,surface,container,primary,onPrimary,onSurface,secondary,outline,primaryContainer,onPrimaryContainer;
    private final Context context;
    MaterialTheme(Context context){
        this.context=context;dark=(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        background=c(dark?"#111318":"#F9F9FF");surface=c(dark?"#191C23":"#FFFFFF");container=c(dark?"#222630":"#EFF1F8");
        primary=c(dark?"#ADC6FF":"#345DA8");onPrimary=c(dark?"#002D68":"#FFFFFF");onSurface=c(dark?"#E2E2EB":"#191C23");
        secondary=c(dark?"#C2C6D3":"#454A57");outline=c(dark?"#8C909E":"#747986");
        primaryContainer=c(dark?"#194581":"#D9E2FF");onPrimaryContainer=c(dark?"#D9E2FF":"#12376D");
    }
    private static int c(String value){return Color.parseColor(value);}
    int dp(int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    GradientDrawable shape(int color,int radius,boolean border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(border)d.setStroke(dp(1),outline);return d;}
    void surface(View view,int color,int radius){view.setBackground(shape(color,radius,false));}
    void typography(TextView view,int size,boolean emphasis){view.setTextSize(size);view.setTextColor(onSurface);view.setTypeface(Typeface.create(emphasis?"sans-serif-medium":"sans-serif",Typeface.NORMAL));view.setLineSpacing(dp(3),1f);}
    void button(Button button,boolean filled){
        button.setAllCaps(false);button.setTextSize(14);button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        button.setMinHeight(dp(52));button.setMinimumHeight(dp(52));button.setPadding(dp(18),dp(8),dp(18),dp(8));button.setElevation(0);
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{secondary,filled?onPrimary:primary}));
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},shape(container,26,false));
        states.addState(new int[]{},shape(filled?primary:surface,26,!filled));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(primaryContainer),states,shape(Color.WHITE,26,false)));
    }
}
