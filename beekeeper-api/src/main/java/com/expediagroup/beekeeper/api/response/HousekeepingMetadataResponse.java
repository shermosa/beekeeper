/**
 * Copyright (C) 2019-2021 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.expediagroup.beekeeper.api.response;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import com.expediagroup.beekeeper.core.model.DurationConverter;
import com.expediagroup.beekeeper.core.model.HousekeepingMetadata;
import com.expediagroup.beekeeper.core.model.HousekeepingStatus;

@Value
@Builder
public class HousekeepingMetadataResponse {

  String path;

  String databaseName;

  String tableName;

  String partitionName;

  @Enumerated(EnumType.STRING)
  HousekeepingStatus housekeepingStatus;

  @EqualsAndHashCode.Exclude
  LocalDateTime creationTimestamp;

  @EqualsAndHashCode.Exclude
  @UpdateTimestamp
  LocalDateTime modifiedTimestamp;

  @EqualsAndHashCode.Exclude
  LocalDateTime cleanupTimestamp;

  @Convert(converter = DurationConverter.class)
  Duration cleanupDelay;

  int cleanupAttempts;

  String lifecycleType;

  public static HousekeepingMetadataResponse convertToHouseKeepingMetadataResponse(
      HousekeepingMetadata housekeepingMetadata) {

    return HousekeepingMetadataResponse.builder()
        .path(housekeepingMetadata.getPath())
        .databaseName(housekeepingMetadata.getDatabaseName())
        .tableName(housekeepingMetadata.getTableName())
        .partitionName(housekeepingMetadata.getPartitionName())
        .housekeepingStatus(housekeepingMetadata.getHousekeepingStatus())
        .creationTimestamp(housekeepingMetadata.getCreationTimestamp())
        .modifiedTimestamp(housekeepingMetadata.getModifiedTimestamp())
        .cleanupTimestamp(housekeepingMetadata.getCleanupTimestamp())
        .cleanupDelay(housekeepingMetadata.getCleanupDelay())
        .cleanupAttempts(housekeepingMetadata.getCleanupAttempts())
        .lifecycleType(housekeepingMetadata.getLifecycleType())
        .build();
  }

  public static Page<HousekeepingMetadataResponse> convertToHouseKeepingMetadataResponsePage(List<HousekeepingMetadata> housekeepingMetadataList){
    List<HousekeepingMetadataResponse> housekeepingMetadataResponseList = new ArrayList<>();
    for (HousekeepingMetadata housekeepingMetadata : housekeepingMetadataList) {
      HousekeepingMetadataResponse housekeepingMetadataResponse = convertToHouseKeepingMetadataResponse(housekeepingMetadata);
      housekeepingMetadataResponseList.add(housekeepingMetadataResponse);
    }
    return new PageImpl<>(housekeepingMetadataResponseList);
  }

}
