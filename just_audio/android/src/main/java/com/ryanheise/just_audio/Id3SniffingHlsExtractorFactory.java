// File: android/src/main/java/com/ryanheise/just_audio/Id3SniffingHlsExtractorFactory.java
package com.ryanheise.just_audio;

import androidx.media3.common.Format;
import androidx.media3.exoplayer.hls.BundledHlsMediaChunkExtractor;
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsMediaChunkExtractor;
import androidx.media3.extractor.TimestampAdjuster;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;

import java.util.List;

/**
 * Wraps the default HLS extractor factory; when the audio stream is MP3 HLS,
 * it swaps the Mp3Extractor for our Id3SniffingMp3Extractor so timed ID3 inside
 * fragments is emitted as application/id3 samples.
 *
 * This signature matches Media3 1.8.0.
 */
public final class Id3SniffingHlsExtractorFactory implements HlsExtractorFactory {

  private final DefaultHlsExtractorFactory delegate =
      new DefaultHlsExtractorFactory(
          DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM,
          /* exposeCea608WhenMissingDeclarations= */ false
      );

  @Override
  public HlsMediaChunkExtractor createExtractor(
      Format format,
      TimestampAdjuster timestampAdjuster,
      List<Format> muxedCaptionFormats,
      List<Format> muxedAudioFormats,
      boolean isMasterPlaylist,
      boolean isTimestampMaster,
      boolean enableEventMessageTrack,
      boolean enableCea608Track
  ) {
    // Let the default factory pick the extractor first
    HlsMediaChunkExtractor base = delegate.createExtractor(
        format,
        timestampAdjuster,
        muxedCaptionFormats,
        muxedAudioFormats,
        isMasterPlaylist,
        isTimestampMaster,
        enableEventMessageTrack,
        enableCea608Track
    );

    // If this is MP3 HLS and the chunk extractor is the bundled one, swap in our sniffer.
    if ("audio/mpeg".equals(format.sampleMimeType)
        && base instanceof BundledHlsMediaChunkExtractor) {
      return new BundledHlsMediaChunkExtractor(
          new Id3SniffingMp3Extractor(),   // <— your sniffer
          format,
          timestampAdjuster
      );
    }

    return base;
  }
}
