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

