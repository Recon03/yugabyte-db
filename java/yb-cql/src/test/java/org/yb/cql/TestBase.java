// Copyright (c) YugaByte, Inc.
package org.yb.cql;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yb.client.MiniYBCluster;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.SyntaxError;

import java.util.Iterator;

public class TestBase {
  protected static final Logger LOG = LoggerFactory.getLogger(TestBase.class);

  protected static final String NUM_MASTERS_PROP = "NUM_MASTERS";
  protected static final int NUM_TABLET_SERVERS = 3;
  protected static final int DEFAULT_NUM_MASTERS = 3;

  // Number of masters that will be started for this test if we're starting
  // a cluster.
  protected static final int NUM_MASTERS =
    Integer.getInteger(NUM_MASTERS_PROP, DEFAULT_NUM_MASTERS);

  protected static MiniYBCluster miniCluster;

  protected static final int DEFAULT_SLEEP = 50000;


  protected Cluster cluster;
  protected Session session;

  @BeforeClass
  public static void SetUpBeforeClass() throws Exception {
    LOG.info("Setting up before class...");

    miniCluster = new MiniYBCluster.MiniYBClusterBuilder()
                  .numMasters(NUM_MASTERS)
                  .numTservers(NUM_TABLET_SERVERS)
                  .defaultTimeoutMs(DEFAULT_SLEEP)
                  .build();

    LOG.info("Waiting for tablet servers...");
    if (!miniCluster.waitForTabletServers(NUM_TABLET_SERVERS)) {
      fail("Couldn't get " + NUM_TABLET_SERVERS + " tablet servers running, aborting");
    }
  }

  @AfterClass
  public static void TearDownAfterClass() throws Exception {
    if (miniCluster != null) {
      miniCluster.shutdown();
    }
  }

  @Before
  public void SetUpBefore() throws Exception {
    cluster = Cluster.builder()
              .addContactPointsWithPorts(miniCluster.getCQLContactPoints())
              // To sniff the CQL wire protocol using Wireshark and debug, uncomment the following
              // line to force the use of CQL V3 protocol. Wireshark does not decode V4 or higher
              // protocol yet.
              // .withProtocolVersion(com.datastax.driver.core.ProtocolVersion.V3)
             .build();
    LOG.info("Connected to cluster: " + cluster.getMetadata().getClusterName());

    session = cluster.connect();
  }

  @After
  public void TearDownAfter() throws Exception {
    session.close();
    cluster.close();
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  protected void CreateTable(String test_table) throws Exception {
    LOG.info("CREATE TABLE " + test_table);
    String create_stmt = String.format("CREATE TABLE %s " +
                    " (h1 int, h2 varchar, " +
                    " r1 int, r2 varchar, " +
                    " v1 int, v2 varchar, " +
                    " primary key((h1, h2), r1, r2));",
            test_table);
    session.execute(create_stmt);
  }

  public void SetupTable(String test_table, int num_rows) throws Exception {
    CreateTable(test_table);

    LOG.info("INSERT INTO TABLE " + test_table);
    for (int idx = 0; idx < num_rows; idx++) {
      // INSERT: Valid statement with column list.
      String insert_stmt = String.format(
        "INSERT INTO %s(h1, h2, r1, r2, v1, v2) VALUES(%d, 'h%d', %d, 'r%d', %d, 'v%d');",
        test_table, idx, idx, idx+100, idx+100, idx+1000, idx+1000);
      session.execute(insert_stmt);
    }
  }

  public void TearDownTable(String test_table) throws Exception {
    LOG.info("DROP TABLE " + test_table);
    String drop_stmt = String.format("DROP TABLE %s;", test_table);
    session.execute(drop_stmt);
  }

  protected Iterator<Row> RunSelect(String tableName, String select_stmt) {
    ResultSet rs = session.execute(select_stmt);
    Iterator<Row> iter = rs.iterator();
    assertTrue(iter.hasNext());
    return iter;
  }
}
