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
    HlsMediaChunkExtractor base = delegate.createExtractor(
        uri, format, muxedAudioFormats, timestampAdjuster, responseHeaders, extractorInput, playerId);

    if ("audio/mpeg".equals(format.sampleMimeType)
        && base instanceof BundledHlsMediaChunkExtractor) {
      return new BundledHlsMediaChunkExtractor(
          new Id3SniffingMp3Extractor(), format, timestampAdjuster);
    }
    return base;
  }
}
