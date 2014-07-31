package com.netflix.elasticcar.monitoring;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.elasticcar.configuration.IConfiguration;
import com.netflix.elasticcar.scheduler.SimpleTimer;
import com.netflix.elasticcar.scheduler.Task;
import com.netflix.elasticcar.scheduler.TaskTimer;
import com.netflix.elasticcar.utils.ESTransportClient;
import com.netflix.elasticcar.utils.ElasticsearchProcessMonitor;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Monitors;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.elasticsearch.indices.NodeIndicesStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class NodeIndicesStatsMonitor extends Task
{
    private static final Logger logger = LoggerFactory.getLogger(NodeIndicesStatsMonitor.class);
    public static final String METRIC_NAME = "Elasticsearch_NodeIndicesMonitor";
    private final Elasticsearch_NodeIndicesStatsReporter nodeIndicesStatsReporter;

    @Inject
    public NodeIndicesStatsMonitor(IConfiguration config)
    {
        super(config);
        nodeIndicesStatsReporter = new Elasticsearch_NodeIndicesStatsReporter();
        Monitors.registerObject(nodeIndicesStatsReporter);
    }

    @Override
    public void execute() throws Exception {

        // If Elasticsearch is started then only start the monitoring
        if (!ElasticsearchProcessMonitor.isElasticsearchStarted()) {
            String exceptionMsg = "Elasticsearch is not yet started, check back again later";
            logger.info(exceptionMsg);
            return;
        }

        NodeIndicesStatsBean nodeIndicesStatsBean = new NodeIndicesStatsBean();
        try
        {
            NodesStatsResponse ndsStatsResponse = ESTransportClient.getNodesStatsResponse(config);
            NodeIndicesStats nodeIndicesStats = null;
            NodeStats ndStat = null;
            if (ndsStatsResponse.getNodes().length > 0) {
                ndStat = ndsStatsResponse.getAt(0);
            }
            if (ndStat == null) {
                logger.info("NodeIndicesStats is null,hence returning (No NodeIndicesStats).");
                return;
            }
            nodeIndicesStats = ndStat.getIndices();
            if (nodeIndicesStats == null) {
                logger.info("NodeIndicesStats is null,hence returning (No NodeIndicesStats).");
                return;
            }

            updateStoreDocs(nodeIndicesStatsBean,nodeIndicesStats);

            updateRefreshFlush(nodeIndicesStatsBean,nodeIndicesStats);

            updateMerge(nodeIndicesStatsBean,nodeIndicesStats);

            updateCache(nodeIndicesStatsBean,nodeIndicesStats);

            updateSearch(nodeIndicesStatsBean,nodeIndicesStats);

            updateGet(nodeIndicesStatsBean,nodeIndicesStats);

            updateIndexing(nodeIndicesStatsBean,nodeIndicesStats);

        }
        catch(Exception e)
        {
            logger.warn("failed to load Trasport stats data", e);
        }

        nodeIndicesStatsReporter.nodeIndicesStatsBean.set(nodeIndicesStatsBean);
    }

    private void updateStoreDocs(NodeIndicesStatsBean nodeIndicesStatsBean, NodeIndicesStats nodeIndicesStats)
    {
        nodeIndicesStatsBean.storeSize = nodeIndicesStats.getStore().getSizeInBytes();
        nodeIndicesStatsBean.storeThrottleTime = nodeIndicesStats.getStore().getThrottleTime().millis();
        nodeIndicesStatsBean.docsCount = nodeIndicesStats.getDocs().getCount();
        nodeIndicesStatsBean.docsDeleted = nodeIndicesStats.getDocs().getDeleted();
    }

    private void updateRefreshFlush(NodeIndicesStatsBean nodeIndicesStatsBean, NodeIndicesStats nodeIndicesStats)
    {
        nodeIndicesStatsBean.refreshTotal = nodeIndicesStats.getRefresh().getTotal();
        nodeIndicesStatsBean.refreshTotalTime = nodeIndicesStats.getRefresh().getTotalTimeInMillis();
        if (nodeIndicesStatsBean.refreshTotal != 0)
            nodeIndicesStatsBean.refreshAvgTimeInMillisPerRequest = nodeIndicesStatsBean.refreshTotalTime / nodeIndicesStatsBean.refreshTotal;

        nodeIndicesStatsBean.flushTotal = nodeIndicesStats.getFlush().getTotal();
        nodeIndicesStatsBean.flushTotalTime = nodeIndicesStats.getFlush().getTotalTimeInMillis();
        if(nodeIndicesStatsBean.flushTotal != 0)
            nodeIndicesStatsBean.flushAvgTimeInMillisPerRequest = nodeIndicesStatsBean.flushTotalTime / nodeIndicesStatsBean.flushTotal;
    }

    private void updateMerge(NodeIndicesStatsBean nodeIndicesStatsBean, NodeIndicesStats nodeIndicesStats)
    {
        nodeIndicesStatsBean.mergesCurrent = nodeIndicesStats.getMerge().getCurrent();
        nodeIndicesStatsBean.mergesCurrentDocs = nodeIndicesStats.getMerge().getCurrentNumDocs();
        nodeIndicesStatsBean.mergesCurrentSize = nodeIndicesStats.getMerge().getCurrentSizeInBytes();
        nodeIndicesStatsBean.mergesTotal = nodeIndicesStats.getMerge().getTotal();
        nodeIndicesStatsBean.mergesTotalTime = nodeIndicesStats.getMerge().getTotalTimeInMillis();
        nodeIndicesStatsBean.mergesTotalSize = nodeIndicesStats.getMerge().getTotalSizeInBytes();
    }

    private void updateCache(NodeIndicesStatsBean nodeIndicesStatsBean, NodeIndicesStats nodeIndicesStats)
    {
        nodeIndicesStatsBean.cacheFieldEvictions = nodeIndicesStats.getFieldData().getEvictions();
        nodeIndicesStatsBean.cacheFieldSize = nodeIndicesStats.getFieldData().getMemorySizeInBytes();
        nodeIndicesStatsBean.cacheFilterEvictions = nodeIndicesStats.getFilterCache().getEvictions();
        nodeIndicesStatsBean.cacheFilterSize = nodeIndicesStats.getFilterCache().getMemorySizeInBytes();
    }

    private void updateSearch(NodeIndicesStatsBean nodeIndicesStatsBean, NodeIndicesStats nodeIndicesStats)
    {
        nodeIndicesStatsBean.searchQueryDelta += (nodeIndicesStats.getSearch().getTotal().getQueryCount() - nodeIndicesStatsBean.searchQueryTotal);
        nodeIndicesStatsBean.searchFetchDelta += (nodeIndicesStats.getSearch().getTotal().getFetchCount() - nodeIndicesStatsBean.searchFetchTotal);
        nodeIndicesStatsBean.searchQueryTotal = nodeIndicesStats.getSearch().getTotal().getQueryCount();
        nodeIndicesStatsBean.searchQueryTime = nodeIndicesStats.getSearch().getTotal().getQueryTimeInMillis();
        if(nodeIndicesStatsBean.searchQueryTotal != 0)
            nodeIndicesStatsBean.searchQueryAvgTimeInMillisPerRequest = nodeIndicesStatsBean.searchQueryTime / nodeIndicesStatsBean.searchQueryTotal;
        nodeIndicesStatsBean.searchQueryCurrent = nodeIndicesStats.getSearch().getTotal().getQueryCurrent();
        nodeIndicesStatsBean.searchFetchTotal = nodeIndicesStats.getSearch().getTotal().getFetchCount();
        nodeIndicesStatsBean.searchFetchTime = nodeIndicesStats.getSearch().getTotal().getFetchTimeInMillis();
        if(nodeIndicesStatsBean.searchFetchTotal != 0)
            nodeIndicesStatsBean.searchFetchAvgTimeInMillisPerRequest = nodeIndicesStatsBean.searchFetchTime / nodeIndicesStatsBean.searchFetchTotal;
        nodeIndicesStatsBean.searchFetchCurrent = nodeIndicesStats.getSearch().getTotal().getFetchCurrent();
    }

    private void updateGet(NodeIndicesStatsBean nodeIndicesStatsBean, NodeIndicesStats nodeIndicesStats)
    {
        nodeIndicesStatsBean.getTotalDelta += (nodeIndicesStats.getGet().getCount() - nodeIndicesStatsBean.getTotal);
        nodeIndicesStatsBean.getExistsDelta += (nodeIndicesStats.getGet().getExistsCount() - nodeIndicesStatsBean.getExistsTotal);
        nodeIndicesStatsBean.getMissingDelta += (nodeIndicesStats.getGet().getMissingCount() - nodeIndicesStatsBean.getMissingDelta);
        nodeIndicesStatsBean.getTotal = nodeIndicesStats.getGet().getCount();
        nodeIndicesStatsBean.getTime = nodeIndicesStats.getGet().getTimeInMillis();
        if (nodeIndicesStatsBean.getTotal != 0)
            nodeIndicesStatsBean.getTotalAvgTimeInMillisPerRequest = nodeIndicesStatsBean.getTime / nodeIndicesStatsBean.getTotal;
        nodeIndicesStatsBean.getCurrent = nodeIndicesStats.getGet().current();
        nodeIndicesStatsBean.getExistsTotal = nodeIndicesStats.getGet().getExistsCount();
        nodeIndicesStatsBean.getExistsTime = nodeIndicesStats.getGet().getExistsTimeInMillis();
        if (nodeIndicesStatsBean.getExistsTotal != 0)
            nodeIndicesStatsBean.getExistsAvgTimeInMillisPerRequest = nodeIndicesStatsBean.getExistsTime / nodeIndicesStatsBean.getExistsTotal;
        nodeIndicesStatsBean.getMissingTotal = nodeIndicesStats.getGet().getMissingCount();
        nodeIndicesStatsBean.getMissingTime = nodeIndicesStats.getGet().getMissingTimeInMillis();
        if (nodeIndicesStatsBean.getMissingTotal != 0)
            nodeIndicesStatsBean.getMissingAvgTimeInMillisPerRequest = nodeIndicesStatsBean.getMissingTime / nodeIndicesStatsBean.getMissingTotal;
    }

    private void updateIndexing(NodeIndicesStatsBean nodeIndicesStatsBean, NodeIndicesStats nodeIndicesStats)
    {
        nodeIndicesStatsBean.indexingIndexDelta += (nodeIndicesStats.getIndexing().getTotal().getIndexCount() - nodeIndicesStatsBean.indexingIndexTotal);
        nodeIndicesStatsBean.indexingDeleteDelta += (nodeIndicesStats.getIndexing().getTotal().getDeleteCount() - nodeIndicesStatsBean.indexingDeleteTotal);
        nodeIndicesStatsBean.indexingIndexTimeInMillis = nodeIndicesStats.getIndexing().getTotal().getIndexTimeInMillis();
        if (nodeIndicesStatsBean.indexingIndexTotal != 0)
            nodeIndicesStatsBean.indexingAvgTimeInMillisPerRequest = nodeIndicesStatsBean.indexingIndexTimeInMillis / nodeIndicesStatsBean.indexingIndexTotal;
        nodeIndicesStatsBean.indexingIndexCurrent = nodeIndicesStats.getIndexing().getTotal().getIndexCurrent();
        nodeIndicesStatsBean.indexingDeleteTime = nodeIndicesStats.getIndexing().getTotal().getDeleteTimeInMillis();
        if (nodeIndicesStatsBean.indexingDeleteTotal != 0)
            nodeIndicesStatsBean.indexingDeleteAvgTimeInMillisPerRequest = nodeIndicesStatsBean.indexingDeleteTime / nodeIndicesStatsBean.indexingDeleteTotal;
        nodeIndicesStatsBean.indexingDeleteCurrent = nodeIndicesStats.getIndexing().getTotal().getDeleteCurrent();
        nodeIndicesStatsBean.indexingIndexTotal = nodeIndicesStats.getIndexing().getTotal().getIndexCount();
        nodeIndicesStatsBean.indexingDeleteTotal = nodeIndicesStats.getIndexing().getTotal().getDeleteCount();
    }

    public class Elasticsearch_NodeIndicesStatsReporter
    {
        private final AtomicReference<NodeIndicesStatsBean> nodeIndicesStatsBean;

        public Elasticsearch_NodeIndicesStatsReporter()
        {
            nodeIndicesStatsBean = new AtomicReference<NodeIndicesStatsBean>(new NodeIndicesStatsBean());
        }

        @Monitor(name ="store_size", type=DataSourceType.GAUGE)
        public long getStoreSize()
        {
            return nodeIndicesStatsBean.get().storeSize;
        }
        @Monitor(name="store_throttle_time", type=DataSourceType.GAUGE)
        public long getStoreThrottleTime()
        {
            return nodeIndicesStatsBean.get().storeThrottleTime;
        }
        @Monitor(name="docs_count", type=DataSourceType.GAUGE)
        public long getDocsCount()
        {
            return nodeIndicesStatsBean.get().docsCount;
        }
        @Monitor(name="docs_deleted", type=DataSourceType.GAUGE)
        public long getDocsDeleted()
        {
            return nodeIndicesStatsBean.get().docsDeleted;
        }

        @Monitor(name="indexing_index_total", type=DataSourceType.GAUGE)
        public long getIndexingIndexTotal() { return nodeIndicesStatsBean.get().indexingIndexTotal; }
        @Monitor(name="indexing_index_time_in_millis", type=DataSourceType.GAUGE)
        public long getIndexingIndexTimeInMillis()
        {
            return nodeIndicesStatsBean.get().indexingIndexTimeInMillis;
        }
        @Monitor(name="indexing_avg_time_in_millis_per_request", type=DataSourceType.GAUGE)
        public double getIndexingAvgTimeInMillisPerRequest()
        {
            return nodeIndicesStatsBean.get().indexingAvgTimeInMillisPerRequest;
        }

        @Monitor(name="indexing_index_current", type=DataSourceType.GAUGE)
        public long getIndexingIndexCurrent()
        {
            return nodeIndicesStatsBean.get().indexingIndexCurrent;
        }

        @Monitor(name="indexing_delete_total", type=DataSourceType.GAUGE)
        public long getIndexingDeleteTotal()
        {
            return nodeIndicesStatsBean.get().indexingDeleteTotal;
        }
        @Monitor(name="indexing_delete_time", type=DataSourceType.GAUGE)
        public long getIndexingDeleteTime()
        {
            return nodeIndicesStatsBean.get().indexingDeleteTime;
        }
        @Monitor(name="indexing_delete_avg_time_in_millis_per_request", type=DataSourceType.GAUGE)
        public double getIndexingDeleteAvgTimeInMillisPerRequest()
        {
            return nodeIndicesStatsBean.get().indexingDeleteAvgTimeInMillisPerRequest;
        }


        @Monitor(name="indexing_delete_current", type=DataSourceType.GAUGE)
        public long getIndexingDeleteCurrent()
        {
            return nodeIndicesStatsBean.get().indexingDeleteCurrent;
        }
        @Monitor(name="indexing_index_delta", type=DataSourceType.COUNTER)
        public long getIndexingIndexDelta()
        {
            return nodeIndicesStatsBean.get().indexingIndexDelta;
        }
        @Monitor(name="indexing_delete_delta", type=DataSourceType.COUNTER)
        public long getIndexingDeleteDelta()
        {
            return nodeIndicesStatsBean.get().indexingDeleteDelta;
        }

        @Monitor(name="get_total", type=DataSourceType.GAUGE)
        public long getGetTotal()
        {
            return nodeIndicesStatsBean.get().getTotal;
        }
        @Monitor(name="get_time", type=DataSourceType.GAUGE)
        public long getGetTime()
        {
            return nodeIndicesStatsBean.get().getTime;
        }
        @Monitor(name="total_avg_time_in_millis_per_request", type=DataSourceType.GAUGE)
        public double getTotalAvgTimeInMillisPerRequest()
        {
            return nodeIndicesStatsBean.get().getTotalAvgTimeInMillisPerRequest;
        }


        @Monitor(name="get_current", type=DataSourceType.GAUGE)
        public long getGetCurrent()
        {
            return nodeIndicesStatsBean.get().getCurrent;
        }
        @Monitor(name="get_exists_total", type=DataSourceType.GAUGE)
        public long getGetExistsTotal()
        {
            return nodeIndicesStatsBean.get().getExistsTotal;
        }
        @Monitor(name="get_exists_time", type=DataSourceType.GAUGE)
        public long getGetExistsTime()
        {
            return nodeIndicesStatsBean.get().getExistsTime;
        }
        @Monitor(name="exists_avg_time_in_millis_per_request", type=DataSourceType.GAUGE)
        public double getExistsAvgTimeInMillisPerRequest()
        {
            return nodeIndicesStatsBean.get().getExistsAvgTimeInMillisPerRequest;
        }


        @Monitor(name="get_missing_total", type=DataSourceType.GAUGE)
        public long getGetMissingTotal()
        {
            return nodeIndicesStatsBean.get().getMissingTotal;
        }
        @Monitor(name="get_missing_time", type=DataSourceType.GAUGE)
        public long getGetMissingTime()
        {
            return nodeIndicesStatsBean.get().getMissingTime;
        }
        @Monitor(name="missing_avg_time_in_millis_per_request", type=DataSourceType.GAUGE)
        public double getMissingAvgTimeInMillisPerRequest()
        {
            return nodeIndicesStatsBean.get().getMissingAvgTimeInMillisPerRequest;
        }


        @Monitor(name="get_total_delta", type=DataSourceType.COUNTER)
        public long getGetTotalDelta()
        {
            return nodeIndicesStatsBean.get().getTotalDelta;
        }
        @Monitor(name="get_exists_delta", type=DataSourceType.COUNTER)
        public long getGetExistsDelta()
        {
            return nodeIndicesStatsBean.get().getExistsDelta;
        }
        @Monitor(name="get_missing_delta", type=DataSourceType.COUNTER)
        public long getGetMissingDelta()
        {
            return nodeIndicesStatsBean.get().getMissingDelta;
        }

        @Monitor(name="search_query_total", type=DataSourceType.GAUGE)
        public long getSearchQueryTotal()
        {
            return nodeIndicesStatsBean.get().searchQueryTotal;
        }
        @Monitor(name="search_query_time", type=DataSourceType.GAUGE)
        public long getSearchQueryTime()
        {
            return nodeIndicesStatsBean.get().searchQueryTime;
        }
        @Monitor(name="search_query_current", type=DataSourceType.GAUGE)
        public long getSearchQueryCurrent()
        {
            return nodeIndicesStatsBean.get().searchQueryCurrent;
        }
        @Monitor(name="search_query_avg_time_in_millis_per_request", type=DataSourceType.GAUGE)
        public double getSearchQueryAvgTimeInMillisPerRequest()
        {
            return nodeIndicesStatsBean.get().searchQueryAvgTimeInMillisPerRequest;
        }


        @Monitor(name="search_query_delta", type=DataSourceType.COUNTER)
        public long getSearchQueryDelta()
        {
            return nodeIndicesStatsBean.get().searchQueryDelta;
        }
        @Monitor(name="search_fetch_total", type=DataSourceType.GAUGE)
        public long getSearchFetchTotal()
        {
            return nodeIndicesStatsBean.get().searchFetchTotal;
        }
        @Monitor(name="search_fetch_time", type=DataSourceType.GAUGE)
        public long getSearchFetchTime()
        {
            return nodeIndicesStatsBean.get().searchFetchTime;
        }
        @Monitor(name="search_fetch_avg_time_in_millis_per_request", type=DataSourceType.GAUGE)
        public double getSearchFetchAvgTimeInMillisPerRequest()
        {
            return nodeIndicesStatsBean.get().searchFetchAvgTimeInMillisPerRequest;
        }


        @Monitor(name="search_fetch_current", type=DataSourceType.GAUGE)
        public long getSearchFetchCurrent()
        {
            return nodeIndicesStatsBean.get().searchFetchCurrent;
        }
        @Monitor(name="search_fetch_delta", type=DataSourceType.COUNTER)
        public long getSearchFetchDelta()
        {
            return nodeIndicesStatsBean.get().searchFetchDelta;
        }

        @Monitor(name="cache_field_evictions", type=DataSourceType.GAUGE)
        public long getCacheFieldEvictions()
        {
            return nodeIndicesStatsBean.get().cacheFieldEvictions;
        }
        @Monitor(name="cache_field_size", type=DataSourceType.GAUGE)
        public long getCacheFieldSize()
        {
            return nodeIndicesStatsBean.get().cacheFieldSize;
        }
        @Monitor(name="cache_filter_evictions", type=DataSourceType.GAUGE)
        public long getCacheFilterEvictions()
        {
            return nodeIndicesStatsBean.get().cacheFilterEvictions;
        }
        @Monitor(name="cache_filter_size", type=DataSourceType.GAUGE)
        public long getCacheFilterSize()
        {
            return nodeIndicesStatsBean.get().cacheFilterSize;
        }

        @Monitor(name="merges_current", type=DataSourceType.GAUGE)
        public long getMergesCurrent()
        {
            return nodeIndicesStatsBean.get().mergesCurrent;
        }
        @Monitor(name="merges_current_docs", type=DataSourceType.GAUGE)
        public long getMergesCurrentDocs()
        {
            return nodeIndicesStatsBean.get().mergesCurrentDocs;
        }
        @Monitor(name="merges_current_size", type=DataSourceType.GAUGE)
        public long getMergesCurrentSize()
        {
            return nodeIndicesStatsBean.get().mergesCurrentSize;
        }
        @Monitor(name="merges_total", type=DataSourceType.GAUGE)
        public long getMergesTotal()
        {
            return nodeIndicesStatsBean.get().mergesTotal;
        }
        @Monitor(name="merges_total_time", type=DataSourceType.GAUGE)
        public long getMergesTotalTime()
        {
            return nodeIndicesStatsBean.get().mergesTotalTime;
        }
        @Monitor(name="merges_total_size", type=DataSourceType.GAUGE)
        public long getMergesTotalSize()
        {
            return nodeIndicesStatsBean.get().mergesTotalSize;
        }


        @Monitor(name="refresh_total", type=DataSourceType.GAUGE)
        public long getRefreshTotal()
        {
            return nodeIndicesStatsBean.get().refreshTotal;
        }
        @Monitor(name="refresh_total_time", type=DataSourceType.GAUGE)
        public long getRefreshTotalTime()
        {
            return nodeIndicesStatsBean.get().refreshTotalTime;
        }
        @Monitor(name="refresh_avg_time_in_millis_per_request", type=DataSourceType.GAUGE)
        public double getRefreshAvgTimeInMillisPerRequest()
        {
            return nodeIndicesStatsBean.get().refreshAvgTimeInMillisPerRequest;
        }

        @Monitor(name="flush_total", type=DataSourceType.GAUGE)
        public long getFlushTotal()
        {
            return nodeIndicesStatsBean.get().flushTotal;
        }
        @Monitor(name="flush_total_time", type=DataSourceType.GAUGE)
        public long getFlushTotalTime()
        {
            return nodeIndicesStatsBean.get().flushTotalTime;
        }
        @Monitor(name="flush_avg_time_in_millis_per_request", type=DataSourceType.GAUGE)
        public double getFlushAvgTimeInMillisPerRequest()
        {
            return nodeIndicesStatsBean.get().flushAvgTimeInMillisPerRequest;
        }

    }

    private static class NodeIndicesStatsBean
    {
        private long storeSize;
        private long storeThrottleTime;
        private long docsCount;
        private long docsDeleted;
        private long indexingIndexTotal;
        private long indexingIndexTimeInMillis;
        private double indexingAvgTimeInMillisPerRequest;
        private long indexingIndexCurrent;
        private long indexingDeleteTotal;
        private long indexingDeleteTime;
        private double indexingDeleteAvgTimeInMillisPerRequest;
        private long indexingDeleteCurrent;
        private long indexingIndexDelta;
        private long indexingDeleteDelta;
        private long getTotal;
        private long getTime;
        private double getTotalAvgTimeInMillisPerRequest;
        private long getCurrent;
        private long getExistsTotal;
        private long getExistsTime;
        private double getExistsAvgTimeInMillisPerRequest;
        private long getMissingTotal;
        private long getMissingTime;
        private double getMissingAvgTimeInMillisPerRequest;
        private long getTotalDelta;
        private long getExistsDelta;
        private long getMissingDelta;
        private long searchQueryTotal;
        private long searchQueryTime;
        private double searchQueryAvgTimeInMillisPerRequest;
        private long searchQueryCurrent;
        private long searchQueryDelta;
        private long searchFetchTotal;
        private long searchFetchTime;
        private double searchFetchAvgTimeInMillisPerRequest;
        private long searchFetchCurrent;
        private long searchFetchDelta;
        private long cacheFieldEvictions;
        private long cacheFieldSize;
        private long cacheFilterEvictions;
        private long cacheFilterSize;
        private long mergesCurrent;
        private long mergesCurrentDocs;
        private long mergesCurrentSize;
        private long mergesTotal;
        private long mergesTotalTime;
        private long mergesTotalSize;
        private long refreshTotal;
        private long refreshTotalTime;
        private double refreshAvgTimeInMillisPerRequest;
        private long flushTotal;
        private long flushTotalTime;
        private double flushAvgTimeInMillisPerRequest;

    }

    public static TaskTimer getTimer(String name)
    {
        return new SimpleTimer(name, 60 * 1000);
    }

    @Override
    public String getName()
    {
        return METRIC_NAME;
    }

}
