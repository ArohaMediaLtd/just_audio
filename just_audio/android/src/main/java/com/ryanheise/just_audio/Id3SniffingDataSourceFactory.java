package com.ryanheise.just_audio;

import androidx.media3.datasource.DataSource;

public final class Id3SniffingDataSourceFactory implements DataSource.Factory {

  public interface MapEmitter {
    void emit(java.util.Map<String, Object> map);
  }

  private final DataSource.Factory upstream;
  private final MapEmitter emitter;

  public Id3SniffingDataSourceFactory(DataSource.Factory upstream, MapEmitter emitter) {
    this.upstream = upstream;
    this.emitter = emitter;
  }

  @Override public DataSource createDataSource() {
    return new Id3SniffingDataSource(upstream.createDataSource(), emitter);
  }
}
