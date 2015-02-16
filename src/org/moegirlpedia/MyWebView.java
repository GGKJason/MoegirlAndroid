package org.moegirlpedia;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.http.util.ByteArrayBuffer;
import org.apache.http.util.EncodingUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.moegirlpedia.database.SQLiteHelper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class MyWebView extends WebView {
	private Boolean loaded = true;
	private Handler mHandler = new Handler();
	private ArrayList<HashMap<String, Object>> listItem = new ArrayList<HashMap<String, Object>>();
	private SQLiteHelper sqliteHelper;
	private SharedPreferences pref;
	private ListView list = null;
	private ProgressBar mprogressBar = null;
	private TextView tvTitle = null;
	private ArrayList<String> history_url = new ArrayList<String>();
	private ArrayList<Integer> history_scroll = new ArrayList<Integer>();
	private String curr_url = "";

	public MyWebView(Context context) {
		this(context, null);
	}

	public MyWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// 初始化
		sqliteHelper = new SQLiteHelper(getContext());
		pref = PreferenceManager.getDefaultSharedPreferences(getContext());

		this.getSettings().setJavaScriptEnabled(true);
		this.getSettings().setUseWideViewPort(false);
		this.getSettings().setBuiltInZoomControls(true);
		this.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
		this.getSettings().setDefaultTextEncodingName("utf-8");
		// this.getSettings().setBlockNetworkImage(true);
		// 设置缓存模式
		this.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
		// 启用缓存
		this.getSettings().setAppCacheEnabled(true);

		final MyWebView that = this;
		this.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				String surl = url;
				if (url.indexOf(getContext().getString(R.string.baseurl)) < 0) {
					callBrowser(url);
					return true;
				}
				else if (url.indexOf("action=edit") >= 0)
				{
					that.gotoEdit();
					return true;
				}
				if ((url.indexOf("?action=render") < 0)
						&& (url.indexOf("?") < 0))
					surl += "?action=render";
				that.loadUrl(surl);
				return true;
			}

			@Override
			public void onPageFinished(WebView view, String url) {
				mprogressBar.setProgress(90);
				listItem.clear();
				String content = GetCache(history_url.size());
				Document doc = Jsoup.parse(content);
				//Elements es = doc.getElementsByClass("mw-headline");
				Element ep = doc.getElementById("toc");
				if (ep != null)
				{
					Elements links = ep.getElementsByTag("a");
					if (links != null)
					{
						for (Element link : links)
						{
							HashMap<String, Object> map = new HashMap<String, Object>();
							map.put("IndexItem",link.text());
							map.put("IndexId",link.attr("href").substring(1));
							listItem.add(map);
						}
					}
				}
				setList();
				tvTitle.setText(that.getTitle());
				
				loaded = true;
				mprogressBar.setVisibility(View.GONE);
				sqliteHelper.add_history(getContext(), that.getTitle(),
						curr_url, 0);
				super.onPageFinished(view, url);
			}

			@Override
			public void onPageStarted(WebView view, String url, Bitmap favicon) {
				super.onPageStarted(view, url, favicon);
			}
		});
		this.setWebChromeClient(new WebChromeClient());

	}

	@Override
	public void loadUrl(String url) {
		if (url.startsWith("javascript:"))
		{
			super.loadUrl(url);
			return;
		}
		
		if (!url.isEmpty()) {
			history_url.add(curr_url);
			history_scroll.add(this.getScrollY());
		}
		String surl = url;
		if ((surl.indexOf("Special:") >= 0) || (surl.indexOf("File:") >= 0))
			surl = surl.replace("?action=render", "");
		curr_url = surl;
		loaded = false;
		fetchURL(surl);

	}

	@Override
	public void goBack() {
		int size = history_url.size();
		int id = size - 1;
		StoreCache(size, "");
		curr_url = history_url.get(id);
		String content = GetCache(id);
		history_url.remove(id);
		loaded = false;
		this.loadDataWithBaseURL(getBaseUrl(curr_url), content, "text/html",
				"UTF-8", "");

		final String url = curr_url;
		final int scroll = history_scroll.get(id);
		history_scroll.remove(id);
		restoreScroll(url, scroll);
	}

	private void restoreScroll(final String url, final int scroll) {
		final MyWebView that = this;
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					while (!loaded) {
						Thread.sleep(100);
					}
					Thread.sleep(400);
				} catch (Exception e) {
				}
				if (url.equals(curr_url)) {
					mHandler.post(new Runnable() {
						@Override
						public void run() {
							that.setScrollY(scroll);
						}
					});
				}
			}
		}).start();
	}

	public void refresh() {
		loaded = false;
		fetchURL(curr_url);
	}

	private void fetchURL(final String url) {
		tvTitle.setText("加载中");
		listItem.clear();
		setList();
		
		this.getSettings().setBlockNetworkImage(
				!pref.getBoolean(
						getContext().getString(R.string.settings_loadimage),
						true));
		if (pref.getBoolean(
				getContext().getString(R.string.settings_loadflash), true))
			this.getSettings().setPluginState(PluginState.ON);
		else
			this.getSettings().setPluginState(PluginState.OFF);

		final String errorPage = getAssetsFile("error.html");
		final String pageHeader = getAssetsFile("pageheader.html");
		final String pageFooter = getAssetsFile("pagefooter.html");
		final String oldcustomize = getAssetsFile("oldcustomize.html");

		final MyWebView that = this;
		mprogressBar.setVisibility(View.VISIBLE);
		mprogressBar.setMax(100);
		mprogressBar.setProgress(20);
		new Thread(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				String realUrl = url;
				String myString = "";
				int responseCode = 0;
				try {
					// 定义获取文件内容的URL
					URL myURL = new URL(url);
					// 打开URL链接
					HttpURLConnection ucon = (HttpURLConnection) myURL
							.openConnection();
					ucon.setUseCaches(true);
					ucon.addRequestProperty("User-Agent", getContext()
							.getString(R.string.useragent));
					responseCode = ucon.getResponseCode();
					// 使用InputStream，从URLConnection读取数据
					InputStream is = ucon.getInputStream();
					BufferedInputStream bis = new BufferedInputStream(is);
					// 用ByteArrayBuffer缓存
					ByteArrayBuffer baf = new ByteArrayBuffer(50);
					int current = 0;
					while ((current = bis.read()) != -1) {
						baf.append((byte) current);
					}
					// 将缓存的内容转化为String,用UTF-8编码
					myString = EncodingUtils.getString(baf.toByteArray(),
							"UTF-8");

					realUrl = ucon.getURL().toString();
				} catch (Exception e) {
					e.printStackTrace();
				}

				if ((!url.equals(curr_url)) || (loaded))
					return;
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						mprogressBar.setProgress(50);
					}
				});

				curr_url = realUrl.replace("index.php?title=", "").replace(
						"&action=render", "?action=render");

				if (responseCode == 404) {
					myString = errorPage.replace("%errorinfo%","词条不存在");
				} else if (myString.isEmpty()) {
					myString = errorPage.replace("%errorinfo%","网络错误");
				} else {
					if (myString.indexOf("登录</a>才能查看其它页面。") >= 0)
						myString = errorPage.replace("%errorinfo%","内容比较糟糕，需要登录");
					else if (myString.indexOf("<div id=\"mw-navigation\">") < 0) {
						myString = pageHeader + "<h2>" + getTitle()
								+ "</h2><hr>" + myString + pageFooter;
					} else {
						try {
							Document doc = Jsoup.parse(myString);
							Element content = doc
									.getElementById("mw-content-text");
							if (curr_url.indexOf("/Mainpage") < 0)
								myString = oldcustomize + "<body>" + "<h2>" + getTitle()
								+ "</h2><hr>" + content.html() + "</body>";
							else
								myString = "<meta name=\"viewport\" content=\"width=device-width, user-scalable=yes, initial-scale=0.9, maximum-scale=0.9, minimum-scale=0.9\">"
										+ oldcustomize
										+ "<body>"
										+ "<h2>"
										+ getTitle()
										+ "</h2><hr>" + content.html() + "</body>";
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}

				mHandler.post(new Runnable() {
					@Override
					public void run() {
						mprogressBar.setProgress(70);
					}
				});

				final String content = myString;
				final int size = history_url.size();
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						that.loadDataWithBaseURL(getBaseUrl(url), content,
								"text/html", "UTF-8", "");
						StoreCache(size, content);
					}
				});
			}
		}).start();
	}

	private String getBaseUrl(String url) {
		String a = url;
		int i = a.indexOf('?');
		if (i != -1)
			a = a.substring(0, i);
		a = a.substring(0, a.lastIndexOf('/'));
		Log.e("baseurl", a);
		return a;
	}

	@Override
	public String getTitle() {
		String a = curr_url;
		int i = a.indexOf('?');
		if (i != -1)
			a = a.substring(0, i);
		i = a.lastIndexOf('/');
		a = a.substring(i + 1, a.length());
		try {
			a = URLDecoder.decode(a, "utf-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return a;
	}

	public void share() {
		String title = getTitle();
		String url = curr_url.replace("?action=render", "");
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_SUBJECT, title);
		intent.putExtra(Intent.EXTRA_TEXT, title + " " + url + " #萌娘百科#");
		getContext().startActivity(
				Intent.createChooser(intent, "分享 - " + title));
	}

	public void openInBrowser() {
		callBrowser(curr_url.replace("?action=render", ""));
	}

	public void gotoEdit() {
		if (curr_url.indexOf("Special:") >= 0) {
			Toast.makeText(getContext(), "本页无法编辑！", Toast.LENGTH_LONG).show();
			return;
		}
		callBrowser(curr_url.replace("action=render", "action=edit"));
	}

	public void addBookmark() {
		sqliteHelper.add_history(getContext(), this.getTitle(), curr_url, 1);
		Toast.makeText(getContext(), "已加入书签！", Toast.LENGTH_LONG).show();
	}

	public void clear() {
		File cachedir = getContext().getCacheDir();
		for (int i = 0; i <= history_url.size(); i++) {
			try {
				new File(cachedir, "" + i).delete();
			} catch (Exception e) {
			}
		}
	}

	@Override
	public boolean canGoBack() {
		if (history_url.size() > 1)
			return true;
		else
			return false;
	}

	@Override
	public WebBackForwardList saveState(Bundle outState) {
		WebBackForwardList ret = super.saveState(outState);
		outState.putSerializable("history_url", history_url);
		outState.putSerializable("history_scroll", history_scroll);
		outState.putString("curr_url", curr_url);
		outState.putInt("scroll", this.getScrollY());
		return ret;
	}

	@Override
	public WebBackForwardList restoreState(Bundle inState) {
		WebBackForwardList ret = super.restoreState(inState);
		
		history_url = (ArrayList<String>) inState
				.getSerializable("history_url");
		history_scroll = (ArrayList<Integer>) inState
				.getSerializable("history_scroll");
		curr_url = inState.getString("curr_url");
		int scroll = inState.getInt("scroll");
		loaded = false;
		this.loadDataWithBaseURL(getBaseUrl(curr_url),
				GetCache(history_url.size()), "text/html", "UTF-8", "");
		restoreScroll(curr_url, scroll);
		return ret;
	}

	public void setIndexListView(ListView lv) {
		list = lv;
		final MyWebView that = this;
		// 添加点击
		list.setOnItemClickListener(new OnItemClickListener() {

				@Override
				public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
										long arg3) {
					String id = (String) listItem.get(arg2).get("IndexId");
					that.loadUrl("javascript:document.getElementById('" + id + "').scrollIntoView();");
					((MainActivity) getContext()).closeDrawerRight();
				}
			});
	}
	
	public void setProgressBar(ProgressBar pb) {
		mprogressBar = pb;
	}

	public void setTextViewTitle(TextView tv) {
		tvTitle = tv;
	}
	
	private void setList() {
		// 生成适配器的Item和动态数组对应的元素
		SimpleAdapter listItemAdapter = new SimpleAdapter(getContext(), listItem,// 数据源
														  R.layout.index_display_style,// ListItem的XML实现
														  new String[] { "IndexItem" }, new int[] { R.id.IndexItem });

		// 添加并且显示
		list.setAdapter(listItemAdapter);
	}
	
	private void callBrowser(final String url) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		Uri content_url = Uri.parse(url);
		intent.setData(content_url);
		getContext().startActivity(intent);
	}

	private void StoreCache(final int id, final String valueToStore) {
		try {
			Context context;
			context = getContext();
			File cachedir = context.getCacheDir();
			File cachefile = new File(cachedir, "" + id);
			FileOutputStream fos = new FileOutputStream(cachefile);
			ObjectOutputStream os = new ObjectOutputStream(fos);
			os.writeObject(valueToStore);
			os.flush();
			os.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			Log.e("TinyDB",
					"File not found! Which is strange because we're trying to save.");

		} catch (IOException e) {
			e.printStackTrace();

		}
	}

	private String GetCache(final int id) {
		Object value = new Object();
		try {
			Context context;
			context = getContext();
			File cachedir = context.getCacheDir();
			File cachefile = new File(cachedir, "" + id);
			FileInputStream filestream = new FileInputStream(cachefile);
			ObjectInputStream ois = new ObjectInputStream(filestream);
			value = ois.readObject();
			ois.close();
		} catch (FileNotFoundException e) {
			Log.e("TinyDB", "File not found!" + " " + id);
			// e.printStackTrace();
			value = "";
		} catch (StreamCorruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		String ret = value.toString();
		if (ret.equals(""))
			ret = getTitle();
		return ret;
	}

	private String readTextFile(InputStream inputStream) {

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		byte buf[] = new byte[1024];

		int len;

		try {

			while ((len = inputStream.read(buf)) != -1) {

				outputStream.write(buf, 0, len);

			}

			outputStream.close();

			inputStream.close();

		} catch (IOException e) {

		}

		return outputStream.toString();
	}

	private String getAssetsFile(String fn) {
		AssetManager assetManager = getContext().getAssets();
		InputStream inputStream = null;
		try {

			inputStream = assetManager.open(fn);

		} catch (Exception e) {

			Log.e("tag", e.getMessage());

		}

		return readTextFile(inputStream);
	}
}
