package org.moegirl.moegirlview;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;

public class SplashActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.activity_splash);

		new Handler().postDelayed(new Runnable() {
			// Ϊ�˼��ٴ���ʹ������Handler����һ����ʱ�ĵ���
			public void run() {
				Intent i = new Intent(SplashActivity.this, MainActivity.class);
				// ͨ��Intent������������������Main���Activity
				SplashActivity.this.startActivity(i); // ����Main����
				SplashActivity.this.finish(); // �ر��Լ����������
			}
		}, 800); // 5�룬�����˰�
	}

	class SplashHandler implements Runnable {
		public void run() {
			startActivity(new Intent(getApplication(), MainActivity.class));
			SplashActivity.this.finish();
		}
	}

}
