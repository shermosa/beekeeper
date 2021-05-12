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
package com.expediagroup.beekeeper.integration.api;

import static java.lang.String.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.OK;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Calendar;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.SocketUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import com.expediagroup.beekeeper.api.BeekeeperApiApplication;
import com.expediagroup.beekeeper.core.model.HousekeepingMetadata;
import com.expediagroup.beekeeper.core.model.HousekeepingStatus;
import com.expediagroup.beekeeper.core.model.LifecycleEventType;
import com.expediagroup.beekeeper.integration.BeekeeperIntegrationTestBase;
import com.expediagroup.beekeeper.integration.utils.BeekeeperApiTestClient;
import com.expediagroup.beekeeper.integration.utils.RestResponsePage;


public class BeekeeperApiIntegrationTest extends BeekeeperIntegrationTestBase {
  
  @Mock
  private Specification<HousekeepingMetadata> spec;

  @Mock
  private Pageable pageable;
  
  @Bean
  @Primary
  public ObjectMapper geObjMapper(){
      return new ObjectMapper()
              .registerModule(new ParameterNamesModule())
              .registerModule(new Jdk8Module())
              .registerModule(new JavaTimeModule());
  }
  

  private static final Logger log = LoggerFactory.getLogger(BeekeeperApiIntegrationTest.class);
  //private DummyHousekeepingMetadataGenerator dummyHousekeepingMetadataGenerator;

  // APP CONTEXT AND TEST CLIENT
  protected static ConfigurableApplicationContext context;
  protected BeekeeperApiTestClient testClient;
  
  protected final ObjectMapper mapper = geObjMapper();

  @BeforeEach
  public void beforeEach() {

    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    int port = SocketUtils.findAvailableTcpPort();
    String[] args = new String[] {
        "--server.port=" + port};
    final String url = format("http://localhost:%d", port);
    log.info("Starting to run Beekeeper API on: {} and args: {}", url, args);
    context = SpringApplication.run(BeekeeperApiApplication.class, args);
    testClient = new BeekeeperApiTestClient(url);
  }
  
  @AfterEach
  public final void afterEach() {
    log.info("Stopping Beekeeper API");
    if (context != null) {
      context.close();
      context = null;
    }
  }
  
  @Test
  public void testGetTablesWhenNoTables() throws SQLException, InterruptedException, IOException {
    HttpResponse<String> response = testClient.getTables();
    assertThat(response.statusCode()).isEqualTo(OK.value());
    String body = response.body();
    Page<HousekeepingMetadata> responsePage = mapper
        .readValue(body, new TypeReference<RestResponsePage<HousekeepingMetadata>>() {});
    assertThat(responsePage.getContent()).isEqualTo(List.of());
  }
  
  @Test
  public void testGetTablesWhenTablesValid() throws SQLException, InterruptedException, IOException {
    
    insertExpiredMetadata("s3://path/to/s3/table", "partition=random/partition");
    insertExpiredMetadata("s3://path/to/s3/table2", "partition=random/partition2");

    HttpResponse<String> response = testClient.getTables();
    assertThat(response.statusCode()).isEqualTo(OK.value());
    String body = response.body();
    Page<HousekeepingMetadata> responsePage = mapper
        .readValue(body, new TypeReference<RestResponsePage<HousekeepingMetadata>>() {});
    List<HousekeepingMetadata> result = responsePage.getContent();
    
    assertHousekeepingMetadata(result.get(0), "s3://path/to/s3/table", "partition=random/partition", "some_table");
    assertHousekeepingMetadata(result.get(1), "s3://path/to/s3/table2", "partition=random/partition2", "some_table");
  }
  
  @Test
  public void testGetTablesWhenTableNameFilter() throws SQLException, InterruptedException, IOException {
    
    insertExpiredMetadata("s3://path/to/s3/table", "partition=random/partition");
    insertExpiredMetadata("s3://path/to/s3/table2", "partition=random/partition2");
    insertExpiredMetadata("bobs_table", "a/path", "a_random_partition", "PT1S");
    
    HttpResponse<String> response = testClient.getTablesWithTableNameFilter("bobs_table");
    assertThat(response.statusCode()).isEqualTo(OK.value());
    String body = response.body();
    Page<HousekeepingMetadata> responsePage = mapper
        .readValue(body, new TypeReference<RestResponsePage<HousekeepingMetadata>>() {});
    List<HousekeepingMetadata> result = responsePage.getContent();
    
    assertHousekeepingMetadata(result.get(0), "a/path", "a_random_partition", "bobs_table");
    assertThat(result.size()).isEqualTo(1);
  }
  
  @Test
  public void testGetTablesWhenDatabaseNameFilter() throws SQLException, InterruptedException, IOException {
    
    insertExpiredMetadata("s3://path/to/s3/table", "partition=random/partition");
    insertExpiredMetadataWithDatabaseName("someones_database");
    
    HttpResponse<String> response = testClient.getTablesWithDatabaseNameFilter("someones_database");
    assertThat(response.statusCode()).isEqualTo(OK.value());
    String body = response.body();
    Page<HousekeepingMetadata> responsePage = mapper
        .readValue(body, new TypeReference<RestResponsePage<HousekeepingMetadata>>() {});
    List<HousekeepingMetadata> result = responsePage.getContent();
    
    assertHousekeepingMetadataDatabaseName(result.get(0), "someones_database");
    assertThat(result.size()).isEqualTo(1);
  }
  
  @Test
  public void testGetTablesWhenHousekeepingStatusFilter() throws SQLException, InterruptedException, IOException {
    
    insertExpiredMetadata("s3://path/to/s3/table", "partition=random/partition");
    insertExpiredMetadataWithHousekeepingStatus(HousekeepingStatus.FAILED);
    
    HttpResponse<String> response = testClient.getTablesWithHousekeepingStatusFilter("FAILED");
    assertThat(response.statusCode()).isEqualTo(OK.value());
    String body = response.body();
    Page<HousekeepingMetadata> responsePage = mapper
        .readValue(body, new TypeReference<RestResponsePage<HousekeepingMetadata>>() {});
    List<HousekeepingMetadata> result = responsePage.getContent();
    
    assertHousekeepingMetadataHousekeepingStatus(result.get(0), HousekeepingStatus.FAILED);
    assertThat(result.size()).isEqualTo(1);
  }

  @Test
  public void testGetTablesWhenLifecycleEventTypeFilter() throws SQLException, InterruptedException, IOException {
    HousekeepingMetadata unreferencedMetadata = createHousekeepingMetadata("myTableName","s3://some/path/","event_date=2020-01-01/event_hour=0/event_type=A",LifecycleEventType.UNREFERENCED,Duration.parse("P3D").toString());
    HousekeepingMetadata testMetadata1 = createHousekeepingMetadata("myTableName2","s3://some/path/","event_date=2020-01-01/event_hour=0/event_type=A",LifecycleEventType.EXPIRED,Duration.parse("P3D").toString());
    HousekeepingMetadata testMetadata2 = createHousekeepingMetadata("myTableName3","s3://some/path/","event_date=2020-01-01/event_hour=0/event_type=A",LifecycleEventType.EXPIRED,Duration.parse("P3D").toString());
    insertExpiredMetadata(unreferencedMetadata);
    insertExpiredMetadata(testMetadata1);
    insertExpiredMetadata(testMetadata2);

    HttpResponse<String> response = testClient.getTablesWithLifecycleEventTypeFilter("UNREFERENCED");
    assertThat(response.statusCode()).isEqualTo(OK.value());
    String body = response.body();
    Page<HousekeepingMetadata> responsePage = mapper
        .readValue(body, new TypeReference<RestResponsePage<HousekeepingMetadata>>() {});
    List<HousekeepingMetadata> result = responsePage.getContent();

    assertTrue(unreferencedMetadata.equals(result.get(0)));
    assertThat(result.size()).isEqualTo(1);
  }
  
  @Test
  public void testGetTablesWhenCleanupTimestampFilter() throws SQLException, InterruptedException, IOException {
    
    insertExpiredMetadata("s3://path/to/s3/table", "partition=random/partition");
    insertExpiredMetadataWithCleanUpDelay();
    
    System.out.println(Calendar.getInstance().toString());
    
    HttpResponse<String> response = testClient.getTablesWithDeletedBeforeFilter("2021-05-05T10:41:20");
    assertThat(response.statusCode()).isEqualTo(OK.value());
    String body = response.body();
    Page<HousekeepingMetadata> responsePage = mapper
        .readValue(body, new TypeReference<RestResponsePage<HousekeepingMetadata>>() {});
    List<HousekeepingMetadata> result = responsePage.getContent();
    
    //assertHousekeepingMetadataLifecycleType(result.get(0), LifecycleEventType.UNREFERENCED);
    //assertThat(result.size()).isEqualTo(1);
  }

}
