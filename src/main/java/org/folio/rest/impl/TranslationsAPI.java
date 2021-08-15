package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Translation;
import org.folio.rest.jaxrs.resource.Translations;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class TranslationsAPI implements Translations {
	public static final Logger logger = LoggerFactory.getLogger(TranslationsAPI.class);
	public static final String TRANSLATION_TABLE = "translation";
	public static final String ID_FIELD = "'id'";

	PostgresClient getPGClient(Context vertxContext, String tenantId) {
		return PostgresClient.getInstance(vertxContext.owner(), tenantId);
	}

	private String getErrorResponse(String response) {
		// Check to see if we're suppressing messages or not
		return response;
	}

	private String logAndSaveError(Throwable err) {
		String message = err.getLocalizedMessage();
		logger.error(message, err);
		return message;
	}

	private String getTenant(Map<String, String> headers) {
		return TenantTool.calculateTenantId(headers.get(RestVerticle.OKAPI_HEADER_TENANT));
	}

	private CQLWrapper getCQL(String query, int limit, int offset, String tableName) throws FieldException {
		CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");
		return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
	}

	private boolean isDuplicate(String errorMessage) {
		if (errorMessage != null && errorMessage.contains("duplicate key value violates unique constraint")) {
			return true;
		}
		return false;
	}

	private boolean isNotPresent(String errorMessage) {
		if (errorMessage != null && errorMessage.contains("is not present in table")) {
			return true;
		}
		return false;
	}

	private boolean isCQLError(Throwable err) {
		if (err.getCause() != null && err.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
			return true;
		}
		return false;
	}

	@Override
	public void getTranslations(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		try {
			String tenantId = getTenant(okapiHeaders);
			PostgresClient pgClient = getPGClient(vertxContext, tenantId);
			CQLWrapper cql = getCQL(query, limit, offset, TRANSLATION_TABLE);
			pgClient.get(TRANSLATION_TABLE, Translation.class, new String[] { "*" }, cql, true, true, getReply -> {
				if (getReply.failed()) {
					String message = logAndSaveError(getReply.cause());
					asyncResultHandler.handle(Future.succeededFuture(
							GetTranslationsResponse.respond500WithTextPlain(getErrorResponse(message))));
				} else {
					List<Translation> transList = (List<Translation>) getReply.result().getResults();
					org.folio.rest.jaxrs.model.Translations trans = new org.folio.rest.jaxrs.model.Translations();
					trans.setTranslations(transList);
					trans.setTotalRecords(getReply.result().getResultInfo().getTotalRecords());
					asyncResultHandler.handle(
							Future.succeededFuture(GetTranslationsResponse.respond200WithApplicationJson(trans)));
				}
			});
		} catch (Exception e) {
			String message = logAndSaveError(e);
			if (isCQLError(e)) {
				message = String.format("CQL Error: %s", message);
			}
			asyncResultHandler.handle(
					Future.succeededFuture(GetTranslationsResponse.respond500WithTextPlain(getErrorResponse(message))));
		}
	}

	@Override
	public void postTranslations(String lang, Translation entity, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		try {
			String tenantId = getTenant(okapiHeaders);
			String id = entity.getId();
			if (id == null) {
				id = UUID.randomUUID().toString();
				entity.setId(id);
			}
			PostgresClient pgClient = getPGClient(vertxContext, tenantId);
			pgClient.save(TRANSLATION_TABLE, id, entity, saveReply -> {
				if (saveReply.failed()) {
					String message = logAndSaveError(saveReply.cause());
					if (isDuplicate(message)) {
						asyncResultHandler.handle(Future.succeededFuture(PostTranslationsResponse
								.respond422WithApplicationJson(ValidationHelper.createValidationErrorMessage("LocaleCode",
										entity.getLocaleCode(), "Language already exists"))));
					} else if (isNotPresent(message)) {
						asyncResultHandler.handle(Future.succeededFuture(PostTranslationsResponse
								.respond422WithApplicationJson(ValidationHelper.createValidationErrorMessage("LocaleCode",
										entity.getLocaleCode(), "Referenced language does not exist"))));
					} else {
						asyncResultHandler.handle(Future.succeededFuture(
								PostTranslationsResponse.respond500WithTextPlain(getErrorResponse(message))));
					}
				} else {
					String ret = saveReply.result();
					entity.setId(ret);
					asyncResultHandler.handle(Future.succeededFuture(PostTranslationsResponse
							.respond201WithApplicationJson(entity, PostTranslationsResponse.headersFor201())));
				}
			});
		} catch (Exception e) {
			String message = logAndSaveError(e);
			asyncResultHandler.handle(Future
					.succeededFuture(PostTranslationsResponse.respond500WithTextPlain(getErrorResponse(message))));
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public void deleteTranslations(String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		try {
			String tenantId = getTenant(okapiHeaders);
			PostgresClient pgClient = getPGClient(vertxContext, tenantId);
			final String DELETE_ALL_QUERY = String.format("DELETE FROM %s_%s.%s", tenantId, "mod_translations",
					TRANSLATION_TABLE);
			logger.info(String.format("Deleting all translations with query %s", DELETE_ALL_QUERY));
			pgClient.mutate(DELETE_ALL_QUERY, mutateReply -> {
				if (mutateReply.failed()) {
					String message = logAndSaveError(mutateReply.cause());
					asyncResultHandler.handle(Future.succeededFuture(
							DeleteTranslationsResponse.respond500WithTextPlain(getErrorResponse(message))));
				} else {
					asyncResultHandler.handle(Future.succeededFuture(DeleteTranslationsResponse.noContent().build()));
				}
			});
		} catch (Exception e) {
			String message = logAndSaveError(e);
			asyncResultHandler.handle(Future
					.succeededFuture(DeleteTranslationsResponse.respond500WithTextPlain(getErrorResponse(message))));
		}
	}

	@Override
	public void getTranslationsByTranslationId(String TranslationId, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		try {
			String tenantId = getTenant(okapiHeaders);
			PostgresClient pgClient = getPGClient(vertxContext, tenantId);
			Criteria idCrit = new Criteria().addField(ID_FIELD).setOperation("=").setValue(TranslationId);
			pgClient.get(TRANSLATION_TABLE, Translation.class, new Criterion(idCrit), true, false, getReply -> {
				if (getReply.failed()) {
					String message = logAndSaveError(getReply.cause());
					asyncResultHandler.handle(Future.succeededFuture(
							GetTranslationsByTranslationIdResponse.respond500WithTextPlain(getErrorResponse(message))));
				} else {
					List<Translation> transList = getReply.result().getResults();
					if (transList.isEmpty()) {
						asyncResultHandler.handle(
								Future.succeededFuture(GetTranslationsByTranslationIdResponse.respond404WithTextPlain(
										String.format("No translations exists with id '%s'", TranslationId))));
					} else {
						Translation trans = transList.get(0);
						asyncResultHandler.handle(Future.succeededFuture(
								GetTranslationsByTranslationIdResponse.respond200WithApplicationJson(trans)));
					}
				}
			});
		} catch (Exception e) {
			String message = logAndSaveError(e);
			asyncResultHandler.handle(Future.succeededFuture(
					GetTranslationsByTranslationIdResponse.respond500WithTextPlain(getErrorResponse(message))));
		}
	}

	@Override
	public void deleteTranslationsByTranslationId(String TranslationId, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		try {
			String tenantId = getTenant(okapiHeaders);
			PostgresClient pgClient = getPGClient(vertxContext, tenantId);
			Criteria idCrit = new Criteria().addField(ID_FIELD).setOperation("=").setValue(TranslationId);
			pgClient.delete(TRANSLATION_TABLE, new Criterion(idCrit), deleteReply -> {
				if (deleteReply.failed()) {
					String message = logAndSaveError(deleteReply.cause());
					asyncResultHandler.handle(Future.succeededFuture(DeleteTranslationsByTranslationIdResponse
							.respond500WithTextPlain(getErrorResponse(message))));
				} else {
					if (deleteReply.result().getUpdated() == 0) {
						asyncResultHandler.handle(Future.succeededFuture(
								DeleteTranslationsByTranslationIdResponse.respond404WithTextPlain("Not found")));
					} else {
						asyncResultHandler
								.handle(Future.succeededFuture(DeleteTranslationsByTranslationIdResponse.respond204()));
					}
				}
			});
		} catch (Exception e) {
			String message = logAndSaveError(e);
			asyncResultHandler.handle(Future.succeededFuture(
					DeleteTranslationsByTranslationIdResponse.respond500WithTextPlain(getErrorResponse(message))));
		}
	}

	@Override
	public void putTranslationsByTranslationId(String TranslationId, String lang, Translation entity,
			Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		try {
			String tenantId = getTenant(okapiHeaders);
			Criteria idCrit = new Criteria().addField(ID_FIELD).setOperation("=").setValue(TranslationId);
			PostgresClient pgClient = getPGClient(vertxContext, tenantId);
			pgClient.update(TRANSLATION_TABLE, entity, new Criterion(idCrit), false, updateReply -> {
				if (updateReply.failed()) {
					String message = logAndSaveError(updateReply.cause());
					asyncResultHandler.handle(Future.succeededFuture(
							PutTranslationsByTranslationIdResponse.respond500WithTextPlain(getErrorResponse(message))));
				} else if (updateReply.result().getUpdated() == 0) {
					asyncResultHandler.handle(Future.succeededFuture(
							PutTranslationsByTranslationIdResponse.respond404WithTextPlain("Not found")));
				} else {
					asyncResultHandler
							.handle(Future.succeededFuture(PutTranslationsByTranslationIdResponse.respond204()));
				}
			});
		} catch (Exception e) {
			String message = logAndSaveError(e);
			asyncResultHandler.handle(Future.succeededFuture(
					PutTranslationsByTranslationIdResponse.respond500WithTextPlain(getErrorResponse(message))));
		}
	}

}
