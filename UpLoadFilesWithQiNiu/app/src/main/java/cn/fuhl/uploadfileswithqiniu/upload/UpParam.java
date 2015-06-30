package cn.fuhl.uploadfileswithqiniu.upload;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class UpParam {
	protected String path;
	protected String mimeType;
	protected String name;
	protected long size;

	public static FileUpParam fileUpParam(File file) {
		try {
			if (file == null || !file.isFile()) {
				throw new FileNotFoundException(file != null ? file.getAbsolutePath() : null);
			}
			FileUpParam p = new FileUpParam();
			p.name = file.getName();
			p.file = file;
			p.size = file.length();
			p.path = file.getPath();
			return p;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static StreamUpParam streamUpParam(InputStream is) {
		return streamUpParam(is, -1);
	}

	public static StreamUpParam streamUpParam(InputStream is, long size) {
		StreamUpParam p = new StreamUpParam();
		p.is = is;
		p.size = size;
		return p;
	}

	/**
	 * @param is
	 * @param size
	 *            流的长度
	 * @return
	 * 
	 *         size > -1 或 is.available() > 1， 否则抛出异常
	 */
	public static StreamUpParam streamCheckUpParam(InputStream is, long size) {
		StreamUpParam p = streamUpParam(is, size);
		checkUpParam(p);
		return p;
	}

	public static void checkUpParam(StreamUpParam p) {
		if (p.getSize() <= -1) {
			throw new RuntimeException("InputStream's length must greater than -1.");
		}
	}

	/**
	 * uri在context 中唯一确定一个文件，若多个文件，取第一个。
	 * 
	 */
	public static StreamUpParam streamUpParam(Uri uri, Context context) {
		StreamUpParam p = new StreamUpParam();
		if ("content".equalsIgnoreCase(uri.getScheme())) {
			Cursor cursor = null;
			try {
				ContentResolver resolver = context.getContentResolver();
				String[] col = {MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DISPLAY_NAME,
						MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATA};
				cursor = resolver.query(uri, col, null, null, null);
				if (cursor != null && cursor.moveToFirst()) {
					int cc = cursor.getColumnCount();
					for (int i = 0; i < cc; i++) {
						String name = cursor.getColumnName(i);
						String value = cursor.getString(i);
						if (MediaStore.MediaColumns.DISPLAY_NAME.equalsIgnoreCase(name)) {
							p.name = value;
						} else if (MediaStore.MediaColumns.SIZE.equalsIgnoreCase(name)) {
							p.size = cursor.getLong(i);
						} else if (MediaStore.MediaColumns.MIME_TYPE.equalsIgnoreCase(name)) {
							p.mimeType = value;
						} else if (MediaStore.MediaColumns.DATA.equalsIgnoreCase(name)) {
							p.path = value;
						}
					}
				}
			} finally {
				if (cursor != null) {
					try {
						cursor.close();
					} catch (Exception e) {
					}
				}
			}
		} else {
			String filePath = uri.getPath();
			try {
				File file = new File(filePath);
				if (file != null && file.isFile()) {
					p.name = file.getName();
					p.size = file.length();
					p.path = file.getPath();
				}
			} catch (Exception e) {

			}
		}

		try {
			p.is = context.getContentResolver().openInputStream(uri);
		} catch (FileNotFoundException e) {

		}

		return p;
	}

	public static class FileUpParam extends UpParam {
		private File file;
		public File getFile() {
			return file;
		}
		public long getSize() {
			if (size <= 0) {
				size = file.length();
			}
			return size;
		}
	}

	public static class StreamUpParam extends UpParam {
		private InputStream is;
		public InputStream getIs() {
			return is;
		}

		public long getSize() {
			if (size <= 0) {
				try {
					size = is.available();
				} catch (IOException e) {
				}
			}
			return size;
		}
	}

	public String getPath() {
		return path;
	}

	public String getMimeType() {
		return mimeType;
	}

	public String getName() {
		return name;
	}

	public long getSize() {
		return size;
	}

}
