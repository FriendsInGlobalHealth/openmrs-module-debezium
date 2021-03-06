package org.openmrs.module.debezium;

import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.kafka.connect.source.SourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.engine.ChangeEvent;

/**
 * Database ChangeEvent consumer which creates a DatabaseEvent instance from the source record and
 * publishes it to all registered consumers.
 */
public class DebeziumChangeConsumer implements Consumer<ChangeEvent<SourceRecord, SourceRecord>> {
	
	private static final Logger log = LoggerFactory.getLogger(DebeziumChangeConsumer.class);
	
	private Consumer<DatabaseEvent> listener;
	
	private Function<ChangeEvent<SourceRecord, SourceRecord>, DatabaseEvent> function = new DbChangeToEventFunction();
	
	private boolean disabled = false;
	
	public DebeziumChangeConsumer(Consumer<DatabaseEvent> listener) {
		this.listener = listener;
	}
	
	@Override
	public void accept(ChangeEvent<SourceRecord, SourceRecord> changeEvent) {
		
		try {
			if (disabled) {
				if (log.isDebugEnabled()) {
					log.info("Deferring processing of change event for later because an error was encountered while "
					        + "processing a previous change event");
				}
				
				return;
			}
			
			DatabaseEvent dbEvent = function.apply(changeEvent);
			
			if (log.isDebugEnabled()) {
				log.debug("Notifying listener of the database event: " + dbEvent);
			}
			
			listener.accept(dbEvent);
		}
		catch (Throwable t) {
			//TODO Do not disable in case of a snapshot event
			disabled = true;
			CustomFileOffsetBackingStore.disable();
			if (log.isDebugEnabled()) {
				log.debug("Disabled change event consumer");
			}
			
			log.error("An error was thrown by the listener while processing database event, stopping debezium engine", t);
			
			//TODO Send a notification to the admin
			DebeziumEngineManager.stopAsync();
			
			Utils.updateGlobalProperty(DebeziumConstants.GP_ENABLED, "false");
		}
		
	}
	
}
