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

    final HlsMediaChunkExtractor base = delegate.createExtractor(
        uri, format, muxedAudioFormats, timestampAdjuster, responseHeaders, extractorInput, playerId);

    // Debug: prove this code path runs and what mime we saw
    android.util.Log.d(TAG, "[HLS-FX] createExtractor mime=" + format.sampleMimeType + " uri=" + uri);

    if ("audio/mpeg".equals(format.sampleMimeType)
        && base instanceof BundledHlsMediaChunkExtractor) {

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
