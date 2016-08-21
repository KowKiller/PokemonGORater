package com.kowsoft.pokemongorater;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class StarterActivity extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_starter);
        findViewById(R.id.startButton).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        startService(new Intent(this, OverlayService.class));
        finish();
    }

}
