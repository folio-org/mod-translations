package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.LanguageTranslator;
import org.folio.rest.jaxrs.resource.LanguageTranslators;
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

public class LanguagesTranslatorsAPI implements LanguageTranslators {
	public static final Logger logger = LoggerFactory.getLogger(LanguagesTranslatorsAPI.class);
	public static final String LANGUAGE_TRANSLATOR_TABLE = "languageTranslator";
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
	public void getLanguageTranslators(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		try {
			String tenantId = getTenant(okapiHeaders);
			PostgresClient pgClient = getPGClient(vertxContext, tenantId);
			CQLWrapper cql = getCQL(query, limit, offset, LANGUAGE_TRANSLATOR_TABLE);
			pgClient.get(LANGUAGE_TRANSLATOR_TABLE, LanguageTranslator.class, new String[] { "*" }, cql, true, true, getReply -> {
				if (getReply.failed()) {
					String message = logAndSaveError(getReply.cause());
					asyncResultHandler.handle(Future.succeededFuture(
							GetLanguageTranslatorsResponse.respond500WithTextPlain(getErrorResponse(message))));
				} else {
					List<LanguageTranslator> LanguageTransList = (List<LanguageTranslator>) getReply.result().getResults();
					org.folio.rest.jaxrs.model.LanguageTranslators locTrans = new org.folio.rest.jaxrs.model.LanguageTranslators();
					locTrans.setLanguageTranslators(LanguageTransList);
					locTrans.setTotalRecords(getReply.result().getResultInfo().getTotalRecords());
					asyncResultHandler.handle(
							Future.succeededFuture(GetLanguageTranslatorsResponse.respond200WithApplicationJson(locTrans)));
				}
			});
		} catch (Exception e) {
			String message = logAndSaveError(e);
			if (isCQLError(e)) {
				message = String.format("CQL Error: %s", message);
			}
			asyncResultHandler.handle(
					Future.succeededFuture(GetLanguageTranslatorsResponse.respond500WithTextPlain(getErrorResponse(message))));
		}
	}

	@Override
	public void postLanguageTranslators(String lang, LanguageTranslator entity, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		try {
			String tenantId = getTenant(okapiHeaders);
			String id = entity.getId();
			if (id == null) {
				id = UUID.randomUUID().toString();
				entity.setId(id);
			}
			PostgresClient pgClient = getPGClient(vertxContext, tenantId);
			pgClient.save(LANGUAGE_TRANSLATOR_TABLE, id, entity, saveReply -> {
				if (saveReply.failed()) {
					String message = logAndSaveError(saveReply.cause());
					if (isDuplicate(message)) {
						asyncResultHandler.handle(Future.succeededFuture(PostLanguageTranslatorsResponse
								.respond422WithApplicationJson(ValidationHelper.createValidationErrorMessage("LocaleCode",
										entity.getLocaleCode(), "Language Exists"))));
					} else if (isNotPresent(message)) {
						asyncResultHandler.handle(Future.succeededFuture(PostLanguageTranslatorsResponse
								.respond422WithApplicationJson(ValidationHelper.createValidationErrorMessage("LocaleCode",
										entity.getLocaleCode(), "Referenced Language does not exist"))));
					} else {
						asyncResultHandler.handle(Future.succeededFuture(
								PostLanguageTranslatorsResponse.respond500WithTextPlain(getErrorResponse(message))));
					}
				} else {
					String ret = saveReply.result();
					entity.setId(ret);
					asyncResultHandler.handle(Future.succeededFuture(PostLanguageTranslatorsResponse
							.respond201WithApplicationJson(entity, PostLanguageTranslatorsResponse.headersFor201())));
				}
			});
		} catch (Exception e) {
			String message = logAndSaveError(e);
			asyncResultHandler.handle(Future
					.succeededFuture(PostLanguageTranslatorsResponse.respond500WithTextPlain(getErrorResponse(message))));
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public void deleteLanguageTranslators(String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		try {
			String tenantId = getTenant(okapiHeaders);
			PostgresClient pgClient = getPGClient(vertxContext, tenantId);
			final String DELETE_ALL_QUERY = String.format("DELETE FROM %s_%s.%s", tenantId, "mod-translations",
					LANGUAGE_TRANSLATOR_TABLE);
			logger.info(String.format("Deleting all Languages translators with query %s", DELETE_ALL_QUERY));
			pgClient.mutate(DELETE_ALL_QUERY, mutateReply -> {
				if (mutateReply.failed()) {
					String message = logAndSaveError(mutateReply.cause());
					asyncResultHandler.handle(Future.succeededFuture(
							DeleteLanguageTranslatorsResponse.respond500WithTextPlain(getErrorResponse(message))));
				} else {
					asyncResultHandler.handle(Future.succeededFuture(DeleteLanguageTranslatorsResponse.noContent().build()));
				}
			});
		} catch (Exception e) {
			String message = logAndSaveError(e);
			asyncResultHandler.handle(Future
					.succeededFuture(DeleteLanguageTranslatorsResponse.respond500WithTextPlain(getErrorResponse(message))));
		}
	}

	@Override
	public void getLanguageTranslatorsByLanguageTranslatorId(String languageTranslatorId, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		try {
			String tenantId = getTenant(okapiHeaders);
			PostgresClient pgClient = getPGClient(vertxContext, tenantId);
			Criteria idCrit = new Criteria().addField(ID_FIELD).setOperation("=").setValue(languageTranslatorId);
			pgClient.get(LANGUAGE_TRANSLATOR_TABLE, LanguageTranslator.class, new Criterion(idCrit), true, false, getReply -> {
				if (getReply.failed()) {
					String message = logAndSaveError(getReply.cause());
					asyncResultHandler.handle(Future.succeededFuture(
							GetLanguageTranslatorsByLanguageTranslatorIdResponse.respond500WithTextPlain(getErrorResponse(message))));
				} else {
					List<LanguageTranslator> LanguageTranslatorList = getReply.result().getResults();
					if (LanguageTranslatorList.isEmpty()) {
						asyncResultHandler.handle(
								Future.succeededFuture(GetLanguageTranslatorsByLanguageTranslatorIdResponse.respond404WithTextPlain(
										String.format("No Language value exists with id '%s'", languageTranslatorId))));
					} else {
						LanguageTranslator LocTranslator = LanguageTranslatorList.get(0);
						asyncResultHandler.handle(Future.succeededFuture(
								GetLanguageTranslatorsByLanguageTranslatorIdResponse.respond200WithApplicationJson(LocTranslator)));
					}
				}
			});
		} catch (Exception e) {
			String message = logAndSaveError(e);
			asyncResultHandler.handle(Future.succeededFuture(
					GetLanguageTranslatorsByLanguageTranslatorIdResponse.respond500WithTextPlain(getErrorResponse(message))));
		}
	}

	@Override
	public void deleteLanguageTranslatorsByLanguageTranslatorId(String languageTranslatorId, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		try {
			String tenantId = getTenant(okapiHeaders);
			PostgresClient pgClient = getPGClient(vertxContext, tenantId);
			Criteria idCrit = new Criteria().addField(ID_FIELD).setOperation("=").setValue(languageTranslatorId);
			pgClient.delete(LANGUAGE_TRANSLATOR_TABLE, new Criterion(idCrit), deleteReply -> {
				if (deleteReply.failed()) {
					String message = logAndSaveError(deleteReply.cause());
					asyncResultHandler.handle(Future.succeededFuture(DeleteLanguageTranslatorsByLanguageTranslatorIdResponse
							.respond500WithTextPlain(getErrorResponse(message))));
				} else {
					if (deleteReply.result().getUpdated() == 0) {
						asyncResultHandler.handle(Future.succeededFuture(
								DeleteLanguageTranslatorsByLanguageTranslatorIdResponse.respond404WithTextPlain("Not found")));
					} else {
						asyncResultHandler
								.handle(Future.succeededFuture(DeleteLanguageTranslatorsByLanguageTranslatorIdResponse.respond204()));
					}
				}
			});
		} catch (Exception e) {
			String message = logAndSaveError(e);
			asyncResultHandler.handle(Future.succeededFuture(
					DeleteLanguageTranslatorsByLanguageTranslatorIdResponse.respond500WithTextPlain(getErrorResponse(message))));
		}
	}

	@Override
	public void putLanguageTranslatorsByLanguageTranslatorId(String languageTranslatorId, String lang, LanguageTranslator entity,
			Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		try {
			String tenantId = getTenant(okapiHeaders);
			Criteria idCrit = new Criteria().addField(ID_FIELD).setOperation("=").setValue(languageTranslatorId);
			PostgresClient pgClient = getPGClient(vertxContext, tenantId);
			pgClient.update(LANGUAGE_TRANSLATOR_TABLE, entity, new Criterion(idCrit), false, updateReply -> {
				if (updateReply.failed()) {
					String message = logAndSaveError(updateReply.cause());
					asyncResultHandler.handle(Future.succeededFuture(
							PutLanguageTranslatorsByLanguageTranslatorIdResponse.respond500WithTextPlain(getErrorResponse(message))));
				} else if (updateReply.result().getUpdated() == 0) {
					asyncResultHandler.handle(Future.succeededFuture(
							PutLanguageTranslatorsByLanguageTranslatorIdResponse.respond404WithTextPlain("Not found")));
				} else {
					asyncResultHandler
							.handle(Future.succeededFuture(PutLanguageTranslatorsByLanguageTranslatorIdResponse.respond204()));
				}
			});
		} catch (Exception e) {
			String message = logAndSaveError(e);
			asyncResultHandler.handle(Future.succeededFuture(
					PutLanguageTranslatorsByLanguageTranslatorIdResponse.respond500WithTextPlain(getErrorResponse(message))));
		}
	}
}
