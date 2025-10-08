package com.ryanheise.just_audio;

import android.net.Uri;

import androidx.media3.common.Format;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.hls.BundledHlsMediaChunkExtractor;
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsMediaChunkExtractor;
import androidx.media3.extractor.ExtractorInput;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Id3SniffingHlsExtractorFactory implements HlsExtractorFactory {

  private static final String TAG = "AudioPlayer";
  private final DefaultHlsExtractorFactory delegate = new DefaultHlsExtractorFactory();

  @Override
  public HlsMediaChunkExtractor createExtractor(
      Uri uri,
      Format format,
      List<Format> muxedAudioFormats,
      TimestampAdjuster timestampAdjuster,
      Map<String, List<String>> responseHeaders,
      ExtractorInput extractorInput,
      PlayerId playerId
  ) throws IOException {

    // Always log what HLS gives us up front
    android.util.Log.d(TAG, "[HLS-FX] createExtractor mime=" + format.sampleMimeType + " uri=" + uri);

    // Build the base extractor first
    HlsMediaChunkExtractor base = delegate.createExtractor(
        uri, format, muxedAudioFormats, timestampAdjuster, responseHeaders, extractorInput, playerId);

    // Decide MP3 based on multiple signals (mime often null at this point)
    boolean looksMp3 = false;

    // 1) URI extension
    final String path = uri.getPath();
    if (path != null && path.toLowerCase(Locale.US).endsWith(".mp3")) {
      looksMp3 = true;
    }

    // 2) Early sample mime if present
    if ("audio/mpeg".equals(format.sampleMimeType)) {
      looksMp3 = true;
    }

    // 3) Response headers (Content-Type can show up here)
    if (!looksMp3 && responseHeaders != null) {
      List<String> ct = responseHeaders.get("Content-Type");
      if (ct == null) ct = responseHeaders.get("content-type");
      if (ct != null) {
        for (String v : ct) {
          if (v != null && v.toLowerCase(Locale.US).contains("audio/mpeg")) {
            looksMp3 = true;
            break;
          }
        }
      }
    }

    if (looksMp3 && base instanceof BundledHlsMediaChunkExtractor) {
      android.util.Log.d(TAG, "[HLS-FX] swapping to Id3SniffingMp3Extractor for MP3 HLS");
      return new BundledHlsMediaChunkExtractor(
          new Id3SniffingMp3Extractor(),  // our sniffer
          format,
          timestampAdjuster
      );
    }

    android.util.Log.d(TAG, "[HLS-FX] using default extractor");
    return base;
  }
}
