// File: android/src/main/java/com/ryanheise/just_audio/Id3SniffingMp3Extractor.java
package com.ryanheise.just_audio;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;

import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.TrackOutput;

import androidx.media3.extractor.metadata.id3.Id3Decoder;
import androidx.media3.extractor.metadata.id3.Id3Frame;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;

import androidx.media3.extractor.mp3.Mp3Extractor;

import java.io.EOFException;

/**
 * Wraps Mp3Extractor and additionally scans the fragment payload for raw ID3 tags
 * (like hls.js FRAG_PARSING_METADATA) and emits them as application/id3 samples
 * so Media3's MetadataRenderer forwards them to AnalyticsListener.onMetadata(...).
 */
public final class Id3SniffingMp3Extractor implements Extractor {
  private final Mp3Extractor delegate = new Mp3Extractor();
  private ExtractorOutput output;
  private TrackOutput id3Track;
  private long timeUs;

  @Override public boolean sniff(ExtractorInput input) { return delegate.sniff(input); }

  @Override public void init(ExtractorOutput output) {
    this.output = output;
    delegate.init(output);

    // A dedicated metadata track for raw ID3 samples we sniff
    id3Track = output.track(/* id= */ 1001, C.TRACK_TYPE_METADATA);
    id3Track.format(new Format.Builder()
        .setId("id3-meta")
        .setSampleMimeType(MimeTypes.APPLICATION_ID3)
        .build()
    );
  }

  @Override public void seek(long position, long timeUs) {
    this.timeUs = timeUs;
    delegate.seek(position, timeUs);
  }

  @Override public void release() { delegate.release(); }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) {
    // Light-weight scan for "ID3" inside the next bytes; if found, decode & emit.
    // We keep this window small to avoid overhead; bump if your tags are further in.
    try {
      input.resetPeekPosition();
      ParsableByteArray head = new ParsableByteArray(10);
      int scanned = 0;
      final int SCAN_WINDOW = 32768; // 32 KiB per call is ample for common HLS tags

      while (scanned < SCAN_WINDOW) {
        boolean ok = input.peekFully(head.getData(), 0, 3, /*allowEndOfInput*/ true);
        if (!ok) break; // end of input

        byte b0 = head.getData()[0], b1 = head.getData()[1], b2 = head.getData()[2];
        if (b0 == 'I' && b1 == 'D' && b2 == '3') {
          // Peek full 10-byte ID3 header
          input.peekFully(head.getData(), 3, 7, /*allowEndOfInput*/ true);

          // Synchsafe size (bytes 6..9)
          int size = ((head.getData()[6] & 0x7f) << 21)
                   | ((head.getData()[7] & 0x7f) << 14)
                   | ((head.getData()[8] & 0x7f) << 7)
                   |  (head.getData()[9] & 0x7f);
          int totalLen = 10 + size;

          // Read the full tag
          byte[] tag = new byte[totalLen];
          input.resetPeekPosition();
          input.peekFully(tag, 0, totalLen, /*allowEndOfInput*/ true);

          // Decode and emit as a metadata sample
          Id3Decoder decoder = new Id3Decoder();
          Metadata meta = decoder.decode(tag, /* size= */ totalLen);
          if (meta != null && meta.length() > 0) {
            // Optional: debug log TIT2/TPE1 if present
            for (int i = 0; i < meta.length(); i++) {
              Id3Frame f = (Id3Frame) meta.get(i);
              if (f instanceof TextInformationFrame) {
                TextInformationFrame tf = (TextInformationFrame) f;
                android.util.Log.d("AudioPlayer", "[ID3-SNIFF] " + tf.id + " = " + tf.value);
              }
            }

            ParsableByteArray sample = new ParsableByteArray(tag);
            id3Track.sampleData(sample, totalLen);
            id3Track.sampleMetadata(
                /* timeUs   = */ timeUs,
                /* flags    = */ C.BUFFER_FLAG_KEY_FRAME,
                /* size     = */ totalLen,
                /* offset   = */ 0,
                /* crypto   = */ null
            );
          }

          // Advance beyond this tag and continue scanning
          input.skipFully(totalLen);
          scanned += totalLen;
          continue;
        } else {
          input.skipFully(1);
          scanned += 1;
        }
      }
    } catch (EOFException ignore) {
      // Delegate will handle end-of-input
    } catch (Exception e) {
      android.util.Log.w("AudioPlayer", "ID3 sniff error", e);
    }

    // Continue normal MP3 extraction
    return delegate.read(input, seekPosition);
  }
}
