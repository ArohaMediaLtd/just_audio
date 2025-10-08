// File: com/ryanheise/just_audio/Id3SniffingMp3Extractor.java
package com.ryanheise.just_audio;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.*;
import androidx.media3.extractor.mp3.Mp3Extractor;
import androidx.media3.extractor.metadata.id3.Id3Decoder;
import androidx.media3.extractor.metadata.Metadata;
import androidx.media3.extractor.metadata.id3.Id3Frame;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;

import java.io.EOFException;

public final class Id3SniffingMp3Extractor implements Extractor {
  private final Mp3Extractor delegate = new Mp3Extractor();
  private ExtractorOutput output;
  private TrackOutput id3Track;
  private long timeUs;

  private static final byte[] ID3 = new byte[] {0x49, 0x44, 0x33}; // "ID3"

  @Override public boolean sniff(ExtractorInput input) { return delegate.sniff(input); }

  @Override public void init(ExtractorOutput output) {
    this.output = output;
    delegate.init(output);

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
    // Sniff for "ID3" anywhere in the upcoming bytes (small peek)
    try {
      input.resetPeekPosition();
      ParsableByteArray scratch = new ParsableByteArray(10);
      long start = input.getPosition();
      int searched = 0;
      while (searched < 32768) { // cap the scan window per read call
        if (input.peekFully(scratch.getData(), 0, 3, /* allowEndOfInput= */ true) != C.RESULT_END_OF_INPUT) {
          if (scratch.getData()[0] == 'I' && scratch.getData()[1] == 'D' && scratch.getData()[2] == '3') {
            // Peek the 10-byte header
            input.peekFully(scratch.getData(), 3, 7, true);
            int size = ((scratch.getData()[6] & 0x7f) << 21)
                     | ((scratch.getData()[7] & 0x7f) << 14)
                     | ((scratch.getData()[8] & 0x7f) << 7)
                     |  (scratch.getData()[9] & 0x7f);
            int total = 10 + size;

            // Read full tag
            byte[] tag = new byte[total];
            input.resetPeekPosition();
            input.peekFully(tag, 0, total, true);

            // Decode ID3 and emit as metadata sample
            Id3Decoder decoder = new Id3Decoder();
            Metadata meta = decoder.decode(tag, /* size= */ total);
            if (meta != null && meta.length() > 0) {
              // Optional: quick TIT2/TPE1 log for debugging
              for (int i = 0; i < meta.length(); i++) {
                Id3Frame f = (Id3Frame) meta.get(i);
                if (f instanceof TextInformationFrame) {
                  TextInformationFrame tf = (TextInformationFrame) f;
                  android.util.Log.d("AudioPlayer", "[ID3-SNIFF] " + tf.id + " = " + tf.value);
                }
              }

              // Send raw ID3 to metadata renderer
              ParsableByteArray sample = new ParsableByteArray(tag);
              id3Track.sampleData(sample, total);
              id3Track.sampleMetadata(
                  /* timeUs= */ timeUs,
                  /* flags= */ C.BUFFER_FLAG_KEY_FRAME,
                  /* size= */ total,
                  /* offset= */ 0,
                  /* cryptoData= */ null
              );
            }

            // Advance past this tag
            input.skipFully(total);
            start += total;
            searched += total;
            continue;
          } else {
            // Advance one byte and keep scanning
            input.skipFully(1);
            start += 1;
            searched += 1;
          }
        } else {
          break;
        }
      }
    } catch (EOFException end) {
      // ignore and let delegate handle
    } catch (Exception e) {
      android.util.Log.w("AudioPlayer", "ID3 sniff error", e);
    }

    // Delegate normal MP3 reading
    return delegate.read(input, seekPosition);
  }
}
