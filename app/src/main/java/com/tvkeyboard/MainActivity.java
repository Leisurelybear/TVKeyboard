package com.tvkeyboard;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.tvkeyboard.phone.PhoneInputActivity;
import com.tvkeyboard.tv.TvInputActivity;

/**
 * Launch screen: user selects TV mode or Phone mode.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_tv_mode).setOnClickListener(v -> {
            startActivity(new Intent(this, TvInputActivity.class));
        });

        findViewById(R.id.btn_phone_mode).setOnClickListener(v -> {
            startActivity(new Intent(this, PhoneInputActivity.class));
        });
    }
}
