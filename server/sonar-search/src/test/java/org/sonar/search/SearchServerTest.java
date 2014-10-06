/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.search;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.process.NetworkUtils;
import org.sonar.process.Props;

import java.util.Properties;

import static org.fest.assertions.Assertions.assertThat;

public class SearchServerTest {

  Integer port;
  String cluster;
  SearchServer searchServer;
  Client client;

  @Before
  public void setUp() throws Exception {
    port = NetworkUtils.freePort();
    cluster = "unitTest";
  }

  @After
  public void tearDown() throws Exception {
    if (searchServer != null) {
      searchServer.stop();
      searchServer.awaitStop();
    }
    if (client != null) {
      client.close();
    }
  }

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public TemporaryFolder temp2 = new TemporaryFolder();

  @Test(timeout = 15000L)
  public void start_stop_server() throws Exception {

    Properties props = new Properties();
    props.put(SearchServer.ES_PORT_PROPERTY, port.toString());
    props.put(SearchServer.ES_CLUSTER_PROPERTY, cluster);
    props.put(SearchServer.SONAR_PATH_HOME, temp.getRoot().getAbsolutePath());

    searchServer = new SearchServer(new Props(props));
    assertThat(searchServer).isNotNull();

    searchServer.start();
    assertThat(searchServer.isReady()).isTrue();

    client = getSearchClient();

    searchServer.stop();
    searchServer.awaitStop();
    searchServer = null;
    try {
      assertThat(client.admin().cluster().prepareClusterStats().get().getStatus()).isNotEqualTo(ClusterHealthStatus.GREEN);
    } catch (NoNodeAvailableException exception) {
      assertThat(exception.getMessage()).isEqualTo("No node available");
    }
  }

  @Test
  public void default_has_no_replication() throws Exception {

    Properties props = new Properties();
    props.put(SearchServer.ES_PORT_PROPERTY, port.toString());
    props.put(SearchServer.ES_CLUSTER_PROPERTY, cluster);
    props.put(SearchServer.SONAR_PATH_HOME, temp.getRoot().getAbsolutePath());

    searchServer = new SearchServer(new Props(props));
    assertThat(searchServer).isNotNull();

    searchServer.start();
    assertThat(searchServer.isReady()).isTrue();

    client = getSearchClient();
    client.admin().indices().prepareCreate("test").get();

    assertThat(client.admin().indices().prepareGetSettings("test")
      .get()
      .getSetting("test", "index.number_of_replicas"))
      .isEqualTo("0");

    searchServer.stop();
    searchServer.awaitStop();
  }

  @Test
  public void cluster_has_replication() throws Exception {

    Properties props = new Properties();
    props.put(SearchServer.CLUSTER_ACTIVATION, Boolean.TRUE.toString());
    props.put(SearchServer.ES_PORT_PROPERTY, port.toString());
    props.put(SearchServer.ES_CLUSTER_PROPERTY, cluster);
    props.put(SearchServer.SONAR_PATH_HOME, temp.getRoot().getAbsolutePath());

    searchServer = new SearchServer(new Props(props));
    assertThat(searchServer).isNotNull();

    searchServer.start();
    assertThat(searchServer.isReady()).isTrue();

    client = getSearchClient();
    client.admin().indices().prepareCreate("test").get();

    assertThat(client.admin().indices().prepareGetSettings("test")
      .get()
      .getSetting("test", "index.number_of_replicas"))
      .isEqualTo("1");

    searchServer.stop();
    searchServer.awaitStop();
  }

  @Test
  public void slave_success_replication() throws Exception {

    Properties props = new Properties();
    props.put(SearchServer.CLUSTER_ACTIVATION, Boolean.TRUE.toString());
    props.put(SearchServer.ES_PORT_PROPERTY, port.toString());
    props.put(SearchServer.SONAR_NODE_NAME, "MASTER");
    props.put(SearchServer.ES_CLUSTER_PROPERTY, cluster);
    props.put(SearchServer.SONAR_PATH_HOME, temp.getRoot().getAbsolutePath());
    searchServer = new SearchServer(new Props(props));
    assertThat(searchServer).isNotNull();

    searchServer.start();
    assertThat(searchServer.isReady()).isTrue();

    client = getSearchClient();
    client.admin().indices().prepareCreate("test").get();

    // start a slave
    props = new Properties();
    props.put(SearchServer.ES_CLUSTER_INET, "localhost:" + port);
    props.put(SearchServer.SONAR_NODE_NAME, "SLAVE");
    props.put(SearchServer.ES_PORT_PROPERTY, NetworkUtils.freePort() + "");
    props.put(SearchServer.ES_CLUSTER_PROPERTY, cluster);
    props.put(SearchServer.SONAR_PATH_HOME, temp2.getRoot().getAbsolutePath());
    SearchServer slaveServer = new SearchServer(new Props(props));
    assertThat(slaveServer).isNotNull();

    slaveServer.start();
    assertThat(slaveServer.isReady()).isTrue();

    assertThat(client.admin().cluster().prepareClusterStats().get()
      .getNodesStats().getCounts().getTotal()).isEqualTo(2);

    searchServer.stop();
    slaveServer.stop();
    searchServer.awaitStop();
    slaveServer.awaitStop();
  }

  @Test
  public void slave_failed_replication() throws Exception {

    Properties props = new Properties();
    props.put(SearchServer.CLUSTER_ACTIVATION, Boolean.FALSE.toString());
    props.put(SearchServer.ES_PORT_PROPERTY, port.toString());
    props.put(SearchServer.SONAR_NODE_NAME, "MASTER");
    props.put(SearchServer.ES_CLUSTER_PROPERTY, cluster);
    props.put(SearchServer.SONAR_PATH_HOME, temp.getRoot().getAbsolutePath());
    searchServer = new SearchServer(new Props(props));
    assertThat(searchServer).isNotNull();

    searchServer.start();
    assertThat(searchServer.isReady()).isTrue();

    client = getSearchClient();
    client.admin().indices().prepareCreate("test").get();

    // start a slave
    props = new Properties();
    props.put(SearchServer.ES_CLUSTER_INET, "localhost:" + port);
    props.put(SearchServer.SONAR_NODE_NAME, "SLAVE");
    props.put(SearchServer.ES_PORT_PROPERTY, NetworkUtils.freePort() + "");
    props.put(SearchServer.ES_CLUSTER_PROPERTY, cluster);
    props.put(SearchServer.SONAR_PATH_HOME, temp2.getRoot().getAbsolutePath());
    SearchServer slaveServer = new SearchServer(new Props(props));
    assertThat(slaveServer).isNotNull();

    try {
      slaveServer.start();
    } catch (Exception e) {
      assertThat(e.getMessage()).isEqualTo(SearchServer.WRONG_MASTER_REPLICATION_FACTOR);
    }

    assertThat(client.admin().cluster().prepareClusterStats().get()
      .getNodesStats().getCounts().getTotal()).isEqualTo(1);

    slaveServer.stop();
    slaveServer.awaitStop();
  }

  private Client getSearchClient() {
    Settings settings = ImmutableSettings.settingsBuilder()
      .put("cluster.name", cluster).build();
    Client client = new TransportClient(settings)
      .addTransportAddress(new InetSocketTransportAddress("localhost", port));
    assertThat(client.admin().cluster().prepareClusterStats().get().getStatus()).isEqualTo(ClusterHealthStatus.GREEN);
    return client;
  }
}