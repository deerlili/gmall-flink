package com.deerlili.gmall.realtime.app.function;

import com.alibaba.fastjson.JSONObject;
import com.deerlili.gmall.realtime.common.HbaseConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;

/**
 * @author lixx
 * @date 2022/6/13
 * @notes hbase upsert dim data
 */
@Slf4j
public class DimSinkFunction extends RichSinkFunction<JSONObject> {

    private Connection connection;

    @Override
    public void open(Configuration parameters) throws Exception {
        // 初始化Phoenix连接
        Class.forName(HbaseConfig.PHOENIX_DRIVER);
        connection = DriverManager.getConnection(HbaseConfig.PHOENIX_SERVER);
        // connection.setAutoCommit(true); = connection.commit();
    }

    @Override
    public void invoke(JSONObject value, Context context) throws Exception {
        // 数据写入Phoenix
        PreparedStatement preparedStatement = null;
        try {
            JSONObject after = value.getJSONObject("after");
            Set<String> keySet = after.keySet();
            Collection<Object> values = after.values();

            // Hbase表名
            String tableName = value.getString("sinkTable");
            // 创建插入数据的SQL
            String upsertSql = genUpsertSql(tableName, keySet, values);
            // 编译
            preparedStatement = connection.prepareStatement(upsertSql);
            // 执行
            preparedStatement.executeUpdate();
            // 提交
            connection.commit();
        } catch (SQLException e) {
            log.error("插入Phoenix数据失败"+e.getMessage());
        } finally {
            if (preparedStatement != null) {
                preparedStatement.close();
            }
        }
    }

    private String genUpsertSql(String tableName, Set<String> keys, Collection<Object> values) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("upsert into ")
                .append(HbaseConfig.HBASE_SCHEMA).append(".").append(tableName)
                .append("(")
                .append(StringUtils.join(keys,","))
                .append(")")
                .append(" values")
                .append("('")
                .append(StringUtils.join(values,"','"))
                .append("')");
        System.out.println(buffer.toString());
        return buffer.toString();
    }
}
