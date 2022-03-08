package io.github.slankka.flinksql;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.catalog.hive.HiveCatalog;
import org.springframework.core.io.ClassPathResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

import javax.xml.transform.stream.StreamSource;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Objects;

@Slf4j
public class FlinkSQLExecutor {

    @NonNull
    public static Sqljob init(String path) {
        InputStream is = null;
        if (path != null) {
            try {
                is = new FileInputStream(path);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream("job.xml");
        }
        Jaxb2Marshaller jaxb2Marshaller = new Jaxb2Marshaller();
        jaxb2Marshaller.setPackagesToScan("io.github.slankka.flinksql");
        try {
            jaxb2Marshaller.setCheckForXmlRootElement(false);
            jaxb2Marshaller.setSchema(new ClassPathResource("schema/job.xsd"));
            jaxb2Marshaller.afterPropertiesSet();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            Object unmarshal = jaxb2Marshaller.unmarshal(new StreamSource(is));
            System.out.println(unmarshal.getClass().getSimpleName());
            return (Sqljob) unmarshal;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Sqljob();
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("<File> is required");
            return;
        }
        Sqljob pfcJob = init(args[0]);
        ConfigType config = pfcJob.getConfig();
        CatalogType catalog = config.getCatalog();

        StreamExecutionEnvironment environment = StreamExecutionEnvironment.getExecutionEnvironment();

        environment.enableCheckpointing(30000);

        StreamTableEnvironment tEnv = StreamTableEnvironment.create(environment);
        tEnv.registerCatalog(catalog.getName(), new HiveCatalog(catalog.getName(), catalog.getDefaultDatabase(), catalog.getHiveConfDir()));

        for (StatementType statement : pfcJob.getStatements().getStatements()) {
            String value = statement.getValue();
            if (Objects.equals(true, statement.getIgnore())) {
                continue;
            }

            if (statement.getId() != null) {
                System.out.println(statement.getId());
                log.info("executing {}", statement.getId());
            }
            tEnv.executeSql(value);
        }
    }
}
