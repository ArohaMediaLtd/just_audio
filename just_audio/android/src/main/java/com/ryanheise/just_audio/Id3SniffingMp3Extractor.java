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
import java.io.IOException;

public final class Id3SniffingMp3Extractor implements Extractor {
  private static final String TAG = "AudioPlayer";

  private final Mp3Extractor delegate = new Mp3Extractor();
  private TrackOutput id3Track;
  private long timeUs;

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    return delegate.sniff(input);
  }

  @Override
  public void init(ExtractorOutput output) {
    delegate.init(output);
    id3Track = output.track(/* id= */ 1001, C.TRACK_TYPE_METADATA);
    id3Track.format(new Format.Builder()
        .setId("id3-meta")
        .setSampleMimeType(MimeTypes.APPLICATION_ID3)
        .build());
    android.util.Log.d(TAG, "[ID3-SNIFF] init complete");
  }

  @Override
  public void seek(long position, long timeUs) {
    this.timeUs = timeUs;
    delegate.seek(position, timeUs);
  }

  @Override
  public void release() {
    delegate.release();
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    // Scan ahead for "ID3" anywhere in the next 256 KiB.
    try {
      input.resetPeekPosition();
      ParsableByteArray head = new ParsableByteArray(10);
      int scanned = 0;
      final int SCAN_WINDOW = 256 * 1024;

      while (scanned < SCAN_WINDOW) {
        boolean ok = input.peekFully(head.getData(), 0, 3, /*allowEndOfInput*/ true);
        if (!ok) break;

        byte b0 = head.getData()[0], b1 = head.getData()[1], b2 = head.getData()[2];
        if (b0 == 'I' && b1 == 'D' && b2 == '3') {
          // Header: 10 bytes
          input.peekFully(head.getData(), 3, 7, /*allowEndOfInput*/ true);

          int size = ((head.getData()[6] & 0x7f) << 21)
                   | ((head.getData()[7] & 0x7f) << 14)
                   | ((head.getData()[8] & 0x7f) << 7)
                   |  (head.getData()[9] & 0x7f);
          int totalLen = 10 + size;

          byte[] tag = new byte[totalLen];
          input.resetPeekPosition();
          input.peekFully(tag, 0, totalLen, /*allowEndOfInput*/ true);

          // Decode and emit
          Id3Decoder decoder = new Id3Decoder();
          Metadata meta = decoder.decode(tag, /* size= */ totalLen);
          if (meta != null && meta.length() > 0) {
            android.util.Log.d(TAG, "[ID3-SNIFF] found ID3 tag len=" + totalLen);
            for (int i = 0; i < meta.length(); i++) {
              Id3Frame f = (Id3Frame) meta.get(i);
              if (f instanceof TextInformationFrame) {
                TextInformationFrame tf = (TextInformationFrame) f;
                // 'value' is deprecated, OK for debugging
                android.util.Log.d(TAG, "[ID3-SNIFF] " + tf.id + " = " + tf.value);
              }
            }

            ParsableByteArray sample = new ParsableByteArray(tag);
            id3Track.sampleData(sample, totalLen);
            id3Track.sampleMetadata(
                /* timeUs= */ timeUs,
                /* flags = */ C.BUFFER_FLAG_KEY_FRAME,
                /* size  = */ totalLen,
                /* offset= */ 0,
                /* crypto= */ null
            );
          }

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
    }

    // Continue normal MP3 decoding
    return delegate.read(input, seekPosition);
  }
}
