package com.github.aleneum.WebbedView.ui.AppMenu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.github.aleneum.WebbedView.R;

import java.lang.ref.WeakReference;
import java.util.Arrays;

public class SideMenuGroup
{
    private final WeakReference<Activity> mActivityRef;
    private final WeakReference<SampleAppMenuInterface> mMenuInterfaceRef;
    private final LinearLayout mLayout;
    private final LayoutParams mLayoutParams;
    private final LayoutInflater inflater;
    private final int dividerResource;

    private final float mEntriesTextSize;
    private final int mEntriesSidesPadding;
    private final int mEntriesUpDownPadding;
    private final Typeface mFont;

    private final int selectorResource;

    private final AppMenu mAppMenu;

    private final OnClickListener mClickListener;
    private final OnCheckedChangeListener mOnCheckedListener;
    private final AdapterView.OnItemSelectedListener mOnSelectedListener;

    
    @SuppressLint("InflateParams")
    public SideMenuGroup(SampleAppMenuInterface menuInterface,
                         Activity context, AppMenu parent, boolean hasTitle, String title,
                         int width)
    {
        mActivityRef = new WeakReference<>(context);
        mMenuInterfaceRef = new WeakReference<>(menuInterface);
        mAppMenu = parent;
        mLayoutParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        
        inflater = LayoutInflater.from(mActivityRef.get());
        mLayout = (LinearLayout) inflater.inflate(
            R.layout.sample_app_menu_group, null, false);
        mLayout.setLayoutParams(new LinearLayout.LayoutParams(width,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        
        mEntriesTextSize = mActivityRef.get().getResources().getDimension(
            R.dimen.menu_entries_text);
        
        mEntriesSidesPadding = (int) mActivityRef.get().getResources().getDimension(
            R.dimen.menu_entries_sides_padding);
        mEntriesUpDownPadding = (int) mActivityRef.get().getResources().getDimension(
            R.dimen.menu_entries_top_down_padding);
        dividerResource = R.layout.sample_app_menu_group_divider;
        
        selectorResource = android.R.drawable.list_selector_background;
        
        mFont = Typeface.create("sans-serif", Typeface.NORMAL);
        
        TextView titleView = mLayout.findViewById(R.id.menu_group_title);
        titleView.setText(title);
        titleView.setTextSize(mActivityRef.get().getResources().getDimension(
            R.dimen.menu_entries_title));
        titleView.setClickable(false);
        
        if (!hasTitle)
        {
            mLayout.removeView(titleView);
            View dividerView = mLayout
                .findViewById(R.id.menu_group_title_divider);
            mLayout.removeView(dividerView);
        }
        
        mClickListener = new OnClickListener()
        {
            
            @Override
            public void onClick(View v)
            {
                int command = Integer.parseInt(v.getTag().toString());
                mMenuInterfaceRef.get().menuProcess(command);
                mAppMenu.hideMenu();
            }
        };

        mOnSelectedListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                int command = Integer.parseInt(adapterView.getTag().toString());
                boolean result = mMenuInterfaceRef.get().menuProcess(command);
                mAppMenu.hideMenu();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        };
        
        mOnCheckedListener = new OnCheckedChangeListener()
        {
            
            @Override
            public void onCheckedChanged(CompoundButton switchView,
                boolean isChecked)
            {
                boolean result;
                int command = Integer.parseInt(switchView.getTag().toString());
                result = mMenuInterfaceRef.get().menuProcess(command);
                if (!result)
                {
                    switchView.setChecked(!isChecked);
                } else
                    mAppMenu.hideMenu();
            }
        };
    }
    
    
    @SuppressWarnings({"deprecation", "UnusedReturnValue"})
    public View addTextItem(String text, int command)
    {
        Drawable selectorDrawable = mActivityRef.get().getResources().getDrawable(
            selectorResource);
        
        TextView newTextView = new TextView(mActivityRef.get());
        newTextView.setText(text);

        newTextView.setBackgroundDrawable(selectorDrawable);
        
        newTextView.setTypeface(mFont);
        newTextView.setTextSize(mEntriesTextSize);
        newTextView.setTag(command);
        newTextView.setVisibility(View.VISIBLE);
        newTextView.setPadding(mEntriesSidesPadding, mEntriesUpDownPadding,
            mEntriesSidesPadding, mEntriesUpDownPadding);
        newTextView.setClickable(true);
        newTextView.setOnClickListener(mClickListener);
        mLayout.addView(newTextView, mLayoutParams);
        
        View divider = inflater.inflate(dividerResource, null);
        mLayout.addView(divider, mLayoutParams);

        return  newTextView;
    }

    public EditText addInputField(String text, int command)
    {
        Drawable selectorDrawable = mActivityRef.get().getResources().getDrawable(
                selectorResource);
        View returnView;

        EditText newEditView = new EditText(mActivityRef.get());
        newEditView.setText(text);

        newEditView.setBackground(selectorDrawable);

        newEditView.setTypeface(mFont);
        newEditView.setTextSize(mEntriesTextSize);
        newEditView.setTag(command);
        newEditView.setVisibility(View.VISIBLE);
        newEditView.setPadding(mEntriesSidesPadding,
                mEntriesUpDownPadding, mEntriesSidesPadding,
                mEntriesUpDownPadding);
        mLayout.addView(newEditView, mLayoutParams);

        View divider = inflater.inflate(dividerResource, null);
        mLayout.addView(divider, mLayoutParams);

        return newEditView;
    }

    public Spinner addDropdown(String[] options, int command, String selected)
    {

        Drawable selectorDrawable = mActivityRef.get().getResources().getDrawable(
                selectorResource);

        Spinner newSpinnerView = new Spinner(mActivityRef.get());
        ArrayAdapter<String> items = new ArrayAdapter<>(mActivityRef.get(), android.R.layout.simple_spinner_item, options);
        newSpinnerView.setAdapter(items);
        newSpinnerView.setSelection(Arrays.asList(options).indexOf(selected));
        newSpinnerView.setBackground(selectorDrawable);
        newSpinnerView.setTag(command);
        newSpinnerView.setVisibility(View.VISIBLE);
        newSpinnerView.setPadding(mEntriesSidesPadding, mEntriesUpDownPadding, mEntriesSidesPadding, mEntriesUpDownPadding);
        newSpinnerView.setOnItemSelectedListener(mOnSelectedListener);
        mLayout.addView(newSpinnerView, mLayoutParams);
        return newSpinnerView;
    }

    // Add a switch menu option
    @SuppressWarnings("deprecation")
    public View addSelectionItem(String text, int command, boolean on)
    {

        Drawable selectorDrawable = mActivityRef.get().getResources().getDrawable(
            selectorResource);
        View returnView;


        Switch newSwitchView = new Switch(mActivityRef.get());
        newSwitchView.setText(text);

        newSwitchView.setBackground(selectorDrawable);

        newSwitchView.setTypeface(mFont);
        newSwitchView.setTextSize(mEntriesTextSize);
        newSwitchView.setTag(command);
        newSwitchView.setVisibility(View.VISIBLE);
        newSwitchView.setPadding(mEntriesSidesPadding,
            mEntriesUpDownPadding, mEntriesSidesPadding,
            mEntriesUpDownPadding);
        newSwitchView.setChecked(on);
        newSwitchView.setOnCheckedChangeListener(mOnCheckedListener);
        mLayout.addView(newSwitchView, mLayoutParams);
        returnView = newSwitchView;

        View divider = inflater.inflate(dividerResource, null);
        mLayout.addView(divider, mLayoutParams);

        return returnView;
    }

    LinearLayout getMenuLayout()
    {
        return mLayout;
    }
}
