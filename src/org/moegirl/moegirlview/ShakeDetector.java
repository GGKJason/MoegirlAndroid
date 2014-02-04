package org.moegirl.moegirlview;

import java.util.ArrayList;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.FloatMath;

/**
 * ���ڼ���ֻ�ҡ��
 * 
 * @author ֣����
 * @see <a href="http://blog.csdn.net/zhengzhiren">Blog</a>
 */
public class ShakeDetector implements SensorEventListener {

	/**
	 * ����ʱ����
	 */
	private static final int UPDATE_INTERVAL = 100;

	/**
	 * ����⵽һ��ҡ�η��������´ο�ʼ���ļ��ʱ�䣨���룩
	 */
	private static final long SHAKE_INTERVAL = 500;

	/**
	 * �Ƿ��״μ�⡣������״μ�⣬Ҫ��mLastX, mLastY, mLastZ�Ƚ��г�ʼ��
	 */
	private boolean mFirstUpdate;

	/**
	 * ��һ�μ���ʱ��
	 */
	private long mLastUpdateTime;

	/**
	 * ��һ�η���ҡ�ε�ʱ��
	 */
	private long mLastShakeTime = 0;

	/**
	 * ��һ�μ��ʱ�����ٶ���x��y��z�����ϵķ��������ں͵�ǰ���ٶȱȽ���
	 */
	private float mLastX, mLastY, mLastZ;

	private SensorManager mSensorManager;
	private ArrayList<OnShakeListener> mListeners;

	/**
	 * ҡ�μ����ֵ�������˶�ҡ�ε����г̶ȣ�ԽСԽ���С�
	 */
	private int mShakeThreshold = 3000;

	/**
	 * ҡ�μ����ֵ�������˶�ҡ�ε����г̶ȣ�ԽСԽ���С�
	 * 
	 * @return
	 */
	public int getShakeThreshold() {
		return mShakeThreshold;
	}

	/**
	 * ҡ�μ����ֵ�������˶�ҡ�ε����г̶ȣ�ԽСԽ���С�
	 */
	public void setShakeThreshold(int threshold) {
		mShakeThreshold = threshold;
	}

	public ShakeDetector(Context context) {
		mSensorManager = (SensorManager) context
				.getSystemService(Context.SENSOR_SERVICE);
		mListeners = new ArrayList<OnShakeListener>();
	}

	/**
	 * ��ҡ���¼�����ʱ������֪ͨ
	 */
	public interface OnShakeListener {
		/**
		 * ���ֻ�ҡ��ʱ������
		 */
		void onShake();
	}

	/**
	 * ע��OnShakeListener����ҡ��ʱ����֪ͨ
	 * 
	 * @param listener
	 */
	public void registerOnShakeListener(OnShakeListener listener) {
		if (!mListeners.contains(listener))
			mListeners.add(listener);
	}

	/**
	 * �Ƴ��Ѿ�ע���OnShakeListener
	 * 
	 * @param listener
	 */
	public void unregisterOnShakeListener(OnShakeListener listener) {
		mListeners.remove(listener);
	}

	/**
	 * ����ҡ�μ��
	 */
	public void start() throws UnsupportedOperationException {
		Sensor sensor = mSensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if (sensor == null) {
			throw new UnsupportedOperationException();
		}
		boolean success = mSensorManager.registerListener(this, sensor,
				SensorManager.SENSOR_DELAY_GAME);
		if (!success) {
			throw new UnsupportedOperationException();
		}
		mFirstUpdate = true;
	}

	/**
	 * ֹͣҡ�μ��
	 */
	public void stop() {
		if (mSensorManager != null)
			mSensorManager.unregisterListener(this);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {

		long currentTime = System.currentTimeMillis();
		float x = event.values[0];
		float y = event.values[1];
		float z = event.values[2];
		long diffTime = currentTime - mLastUpdateTime;

		// �״μ�⣬���г�ʼ��
		if (mFirstUpdate) {
			mLastX = x;
			mLastY = y;
			mLastZ = z;
			mLastUpdateTime = currentTime;
			mFirstUpdate = false;
			return;
		}

		// ���μ��ļ��
		if (diffTime < UPDATE_INTERVAL) {
			return;
		}

		// ����ҡ�εļ��
		if (currentTime - mLastShakeTime < SHAKE_INTERVAL) {
			mLastX = x;
			mLastY = y;
			mLastZ = z;
			mLastUpdateTime = currentTime;
			return;
		}

		float deltaX = x - mLastX;
		float deltaY = y - mLastY;
		float deltaZ = z - mLastZ;

		mLastX = x;
		mLastY = y;
		mLastZ = z;
		mLastUpdateTime = currentTime;

		// ���ٶȲ�ֵ
		float delta = FloatMath.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ
				* deltaZ)
				/ diffTime * 10000;

		// ����ֵ����ָ������ֵ����Ϊ����һ��ҡ��
		if (delta > mShakeThreshold) {
			mLastShakeTime = currentTime;
			notifyListeners();
		}
	}

	/**
	 * ��ҡ���¼�����ʱ��֪ͨ���е�listener
	 */
	private void notifyListeners() {
		for (OnShakeListener listener : mListeners) {
			listener.onShake();
		}
	}

}