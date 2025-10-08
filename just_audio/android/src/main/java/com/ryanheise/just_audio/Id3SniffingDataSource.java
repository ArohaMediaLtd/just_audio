package com.ryanheise.just_audio;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.extractor.metadata.id3.Id3Decoder;
import androidx.media3.extractor.metadata.id3.Id3Frame;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Wraps a DataSource and scans the downloaded bytes for raw ID3v2 tags.
 * When one is found, the tag is decoded and TIT2 / TPE1 / TXXX are forwarded
 * to the provided MapEmitter (which posts on the main thread to EventChannel).
 */
final class Id3SniffingDataSource implements DataSource {

  private static final String TAG = "AudioPlayer";
  private static final byte[] ID3 = new byte[]{ 'I','D','3' };
  private static final int    MAX_WINDOW = 256 * 1024; // 256 KiB sliding window

  private final DataSource upstream;
  private final Id3SniffingDataSourceFactory.MapEmitter emitter;

  private final ArrayList<Byte> window = new ArrayList<>(MAX_WINDOW);
  private int scanPos = 0;   // where our search continues in the window
  private long totalRead = 0;

  Id3SniffingDataSource(DataSource upstream,
                        Id3SniffingDataSourceFactory.MapEmitter emitter) {
    this.upstream = upstream;
    this.emitter  = emitter;
  }

  @Override public void addTransferListener(TransferListener transferListener) {
    upstream.addTransferListener(transferListener);
  }

  @Override public long open(DataSpec dataSpec) throws IOException {
    return upstream.open(dataSpec);
  }

  @Override public int read(byte[] buffer, int offset, int length) throws IOException {
    int n = upstream.read(buffer, offset, length);
    if (n > 0) {
      totalRead += n;
      // Append to window
      appendToWindow(buffer, offset, n);
      // Scan window for ID3
      tryScanWindow();
    }
    return n;
  }

  @Nullable @Override public Uri getUri() { return upstream.getUri(); }

  @Override public Map<String, java.util.List<String>> getResponseHeaders() {
    return upstream.getResponseHeaders();
  }

  @Override public void close() throws IOException {
    upstream.close();
    window.clear();
    scanPos = 0;
  }

  // ---- helpers ----

  private void appendToWindow(byte[] buf, int off, int len) {
    // trim if we exceed MAX_WINDOW
    int free = MAX_WINDOW - window.size();
    if (len > free) {
      int drop = len - free;
      if (drop > 0 && drop < window.size()) {
        // remove from the start; adjust scanPos accordingly
        for (int i = 0; i < drop; i++) window.remove(0);
        scanPos = Math.max(0, scanPos - drop);
      } else if (drop >= window.size()) {
        window.clear();
        scanPos = 0;
      }
    }
    for (int i = 0; i < len; i++) window.add(buf[off + i]);
  }

  private void tryScanWindow() {
    byte[] w = toByteArray(window);
    int limit = w.length;

    while (scanPos + 10 <= limit) {
      // find 'ID3'
      int idx = indexOf(w, ID3, scanPos, limit);
      if (idx < 0) {
        // move scanPos near end so we resume on next read
        scanPos = Math.max(0, limit - 10);
        return;
      }
      // Need at least 10 header bytes
      if (idx + 10 > limit) {
        scanPos = idx; // wait for more data
        return;
      }
      // synchsafe size at bytes 6..9 from header start
      int size = synchsafeInt(w[idx + 6], w[idx + 7], w[idx + 8], w[idx + 9]);
      int total = 10 + size;
      if (idx + total > limit) {
        // not enough bytes yet, wait
        scanPos = idx;
        return;
      }

      // Extract tag and decode
      byte[] tag = new byte[total];
      System.arraycopy(w, idx, tag, 0, total);
      emitTag(tag);

      // advance scan past this tag
      scanPos = idx + total;
    }
  }

  private void emitTag(byte[] tag) {
    try {
      Id3Decoder decoder = new Id3Decoder();
      Metadata meta = decoder.decode(tag, tag.length);
      if (meta == null || meta.length() == 0) return;

      for (int i = 0; i < meta.length(); i++) {
        Id3Frame f = (Id3Frame) meta.get(i);
        if (f instanceof TextInformationFrame) {
          TextInformationFrame tf = (TextInformationFrame) f;
          String id  = tf.id != null ? tf.id.toUpperCase(Locale.US) : "";
          // NOTE: tf.value is deprecated but fine for reading
          String val = tf.value != null ? tf.value.toString().trim() : null;
          if (val == null || val.isEmpty()) continue;

          if ("TIT2".equals(id)) {
            emitter.emit(mapOf("type","id3","id","TIT2","value", val));
          } else if ("TPE1".equals(id)) {
            emitter.emit(mapOf("type","id3","id","TPE1","value", val));
          } else if ("TXXX".equals(id)) {
            // We don't have easy access to description here; send unknown desc
            emitter.emit(mapOf("type","id3-txxx","description","", "value", val));
          }
        }
      }
    } catch (Throwable t) {
      // swallow; keep streaming
      android.util.Log.w(TAG, "ID3 decode failed", t);
    }
  }

  private static int indexOf(byte[] hay, byte[] needle, int from, int to) {
    outer:
    for (int i = from; i <= to - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (hay[i + j] != needle[j]) continue outer;
      }
      return i;
    }
    return -1;
  }

  private static int synchsafeInt(byte b6, byte b7, byte b8, byte b9) {
    return ((b6 & 0x7f) << 21) | ((b7 & 0x7f) << 14) | ((b8 & 0x7f) << 7) | (b9 & 0x7f);
  }

  private static byte[] toByteArray(ArrayList<Byte> list) {
    byte[] out = new byte[list.size()];
    for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
    return out;
  }

  private static Map<String,Object> mapOf(Object... kv) {
    Map<String,Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String)kv[i], kv[i+1]);
    return m;
  }
}
