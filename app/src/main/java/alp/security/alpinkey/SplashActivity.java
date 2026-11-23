package alp.security.alpinkey;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import alp.alpinsense.webauthn.util.WioBiometricPrompt;
import alp.alpinsense.webauthn.util.WioRequestDialog;

public class SplashActivity extends AppCompatActivity {

    private final static String TAG = "AlpinKey|SplashActivity";
    private final static int ANIMATION_START_TIMER = 500;
    Animation anim,anim2;
    View imageLogo;
    ImageView imageLogoElement;
    ConstraintLayout splashLayout;

    private static KeyguardManager keyguardManager;

    private View.OnClickListener mSplashListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (keyguardManager.isDeviceSecure()) {
                biometricCheck();
            } else {
                requestAddBiometrics();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        getSupportActionBar().hide();

        keyguardManager = (KeyguardManager) this.getSystemService(Context.KEYGUARD_SERVICE);
        /*
         * Animation
         */

        splashLayout = findViewById(R.id.layout_splash);
        imageLogo = findViewById(R.id.logo_name);
        imageLogoElement = findViewById(R.id.logo_element);

        Handler handler = new Handler(getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                animateLogoIn(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {}

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        if (keyguardManager.isDeviceSecure()) {
                            biometricCheck();
                        } else {
                            requestAddBiometrics();
                        }
                        splashLayout.setOnClickListener(mSplashListener);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {}
                });
            }
        });

    }

    @Override
    public void onStop(){
        super.onStop();
    }

    private void animateLogoIn(Animation.AnimationListener animationListener) {
        anim = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_in);
        anim2 = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_in);
        anim2.setAnimationListener(animationListener);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                imageLogo.startAnimation(anim);
                imageLogoElement.startAnimation(anim2);
            }
        }, ANIMATION_START_TIMER);
    }

    private void requestAddBiometrics() {
        //No Device Security. Disallow app usage.
        WioRequestDialog dialog = WioRequestDialog.create(
                getString(R.string.biometrics_noBiometricsTitle),
                getString(R.string.biometrics_noBiometricsSubtitle),
                new WioRequestDialog.PromptCallback() {
                    @Override
                    public void onResult(boolean result){
                        if(result){
                            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                        } else {
                            Toast.makeText(SplashActivity.this,
                                    getString(R.string.biometrics_credentialsExplanation),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
        dialog.show(this);
    }

    private void biometricCheck() {
        WioBiometricPrompt.registerCallback(new WioBiometricPrompt.PromptCallback(true) {
            @Override
            public void onResult(boolean result, BiometricPrompt.CryptoObject cryptoObject) {
                if (result) {
                    // Remove blocking screen and go to home
                    MainActivity.markAuthenticatedSinceSplash();
                    Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(SplashActivity.this,
                            getString(R.string.biometrics_lockScreenExplanation),
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        new WioBiometricPrompt(this,
                                getString(R.string.biometrics_promptTitle),
                                getString(R.string.biometrics_promptSubtitle),
                                true);
    }
}
