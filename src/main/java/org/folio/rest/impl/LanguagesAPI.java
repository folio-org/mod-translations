package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Language;
import org.folio.rest.jaxrs.resource.Languages;
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

public class LanguagesAPI implements Languages {
	public static final Logger logger = LoggerFactory.getLogger(LanguagesAPI.class);
	public static final String LANGUAGES_TABLE = "language";
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

	private boolean isCQLError(Throwable err) {
		if (err.getCause() != null && err.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
			return true;
		}
		return false;
	}

	@Override
	public void getLanguages(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		vertxContext.runOnContext(v -> {
			try {
				String tenantId = getTenant(okapiHeaders);
				PostgresClient pgClient = getPGClient(vertxContext, tenantId);
				CQLWrapper cql = getCQL(query, limit, offset, LANGUAGES_TABLE);
				pgClient.get(LANGUAGES_TABLE, Language.class, new String[] { "*" }, cql, true, true, getReply -> {
					if (getReply.failed()) {
						String message = logAndSaveError(getReply.cause());
						asyncResultHandler.handle(Future.succeededFuture(
								GetLanguagesResponse.respond500WithTextPlain(getErrorResponse(message))));
					} else {
						org.folio.rest.jaxrs.model.Languages Languages = new org.folio.rest.jaxrs.model.Languages();
						List<Language> LanguageList = getReply.result().getResults();
						Languages.setLanguages(LanguageList);
						Languages.setTotalRecords(getReply.result().getResultInfo().getTotalRecords());
						asyncResultHandler.handle(
								Future.succeededFuture(GetLanguagesResponse.respond200WithApplicationJson(Languages)));
					}
				});
			} catch (Exception e) {
				String message = logAndSaveError(e);
				if (isCQLError(e)) {
					message = String.format("CQL Error: %s", message);
				}
				asyncResultHandler.handle(Future
						.succeededFuture(GetLanguagesResponse.respond500WithTextPlain(getErrorResponse(message))));
			}
		});
	}

	@Override
	public void postLanguages(String lang, Language entity, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		vertxContext.runOnContext(v -> {
			try {

				String id = entity.getId();
				if (id == null) {
					id = UUID.randomUUID().toString();
					entity.setId(id);
				}
				String tenantId = getTenant(okapiHeaders);
				PostgresClient pgClient = getPGClient(vertxContext, tenantId);
				pgClient.save(LANGUAGES_TABLE, id, entity, saveReply -> {
					if (saveReply.failed()) {
						String message = logAndSaveError(saveReply.cause());
						if (isDuplicate(message)) {
							asyncResultHandler.handle(Future.succeededFuture(PostLanguagesResponse
									.respond422WithApplicationJson(ValidationHelper.createValidationErrorMessage("LocaleCode",
											entity.getLocaleCode(), "Language Exists"))));
						} else {
							asyncResultHandler.handle(Future.succeededFuture(
									PostLanguagesResponse.respond500WithTextPlain(getErrorResponse(message))));
						}
					} else {
						String ret = saveReply.result();
						entity.setId(ret);
						asyncResultHandler.handle(Future.succeededFuture(PostLanguagesResponse
								.respond201WithApplicationJson(entity, PostLanguagesResponse.headersFor201())));
					}
				});
			} catch (Exception e) {
				String message = logAndSaveError(e);
				asyncResultHandler.handle(Future
						.succeededFuture(PostLanguagesResponse.respond500WithTextPlain(getErrorResponse(message))));
			}
		});
	}

	@SuppressWarnings("deprecation")
	@Override
	public void deleteLanguages(String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		vertxContext.runOnContext(v -> {
			try {
				String tenantId = getTenant(okapiHeaders);
				PostgresClient pgClient = getPGClient(vertxContext, tenantId);
				final String DELETE_ALL_QUERY = String.format("DELETE FROM %s_%s.%s", tenantId, "mod-translations",
						LANGUAGES_TABLE);
				logger.info(String.format("Deleting all Languages with query %s", DELETE_ALL_QUERY));
				pgClient.mutate(DELETE_ALL_QUERY, mutateReply -> {
					if (mutateReply.failed()) {
						String message = logAndSaveError(mutateReply.cause());
						asyncResultHandler.handle(Future.succeededFuture(
								DeleteLanguagesResponse.respond500WithTextPlain(getErrorResponse(message))));
					} else {
						asyncResultHandler.handle(Future.succeededFuture(DeleteLanguagesResponse.noContent().build()));
					}
				});
			} catch (Exception e) {
				String message = logAndSaveError(e);
				asyncResultHandler.handle(Future
						.succeededFuture(DeleteLanguagesResponse.respond500WithTextPlain(getErrorResponse(message))));
			}
		});
	}

	@Override
	public void putLanguagesByLanguageId(String languageId, String lang, Language entity,
			Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		vertxContext.runOnContext(v -> {
			try {

				String tenantId = getTenant(okapiHeaders);
				PostgresClient pgClient = getPGClient(vertxContext, tenantId);
				Criteria idCrit = new Criteria().addField(ID_FIELD).setOperation("=").setValue(languageId);
				pgClient.update(LANGUAGES_TABLE, entity, new Criterion(idCrit), false, updateReply -> {
					if (updateReply.failed()) {
						String message = logAndSaveError(updateReply.cause());
						asyncResultHandler.handle(Future.succeededFuture(
								PutLanguagesByLanguageIdResponse.respond500WithTextPlain(getErrorResponse(message))));
					} else if (updateReply.result().getUpdated() == 0) {
						asyncResultHandler.handle(Future.succeededFuture(
								PutLanguagesByLanguageIdResponse.respond404WithTextPlain("Not found")));
					} else {
						asyncResultHandler
								.handle(Future.succeededFuture(PutLanguagesByLanguageIdResponse.respond204()));
					}
				});
			} catch (Exception e) {
				String message = logAndSaveError(e);
				asyncResultHandler.handle(Future.succeededFuture(
						PutLanguagesByLanguageIdResponse.respond500WithTextPlain(getErrorResponse(message))));
			}
		});
	}

	@Override
	public void getLanguagesByLanguageId(String languageId, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		vertxContext.runOnContext(v -> {
			try {
				String tenantId = getTenant(okapiHeaders);
				PostgresClient pgClient = getPGClient(vertxContext, tenantId);
				Criteria idCrit = new Criteria().addField(ID_FIELD).setOperation("=").setValue(languageId);
				pgClient.get(LANGUAGES_TABLE, Language.class, new Criterion(idCrit), true, false, getReply -> {
					if (getReply.failed()) {
						String message = logAndSaveError(getReply.cause());
						asyncResultHandler.handle(Future.succeededFuture(
								GetLanguagesByLanguageIdResponse.respond500WithTextPlain(getErrorResponse(message))));
					} else {
						List<Language> LanguageList = getReply.result().getResults();
						if (LanguageList.isEmpty()) {
							asyncResultHandler.handle(
									Future.succeededFuture(GetLanguagesByLanguageIdResponse.respond404WithTextPlain(
											String.format("No Language exists with id '%s'", languageId))));
						} else {
							Language Language = LanguageList.get(0);
							asyncResultHandler.handle(Future.succeededFuture(
									GetLanguagesByLanguageIdResponse.respond200WithApplicationJson(Language)));
						}
					}
				});
			} catch (Exception e) {
				String message = logAndSaveError(e);
				asyncResultHandler.handle(Future.succeededFuture(
						GetLanguagesByLanguageIdResponse.respond500WithTextPlain(getErrorResponse(message))));
			}
		});
	}

	@Override
	public void deleteLanguagesByLanguageId(String languageId, String lang, Map<String, String> okapiHeaders,
			Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		vertxContext.runOnContext(v -> {
			try {
				String tenantId = getTenant(okapiHeaders);
				PostgresClient pgClient = getPGClient(vertxContext, tenantId);
				checkLanguageInUse().setHandler(inUseRes -> {
					if (inUseRes.failed()) {
						String message = logAndSaveError(inUseRes.cause());
						asyncResultHandler.handle(Future.succeededFuture(DeleteLanguagesByLanguageIdResponse
								.respond500WithTextPlain(getErrorResponse(message))));
					} else if (inUseRes.result()) {
						asyncResultHandler.handle(Future.succeededFuture(DeleteLanguagesByLanguageIdResponse
								.respond400WithTextPlain("Cannot delete Language, as it is in use")));
					} else {
						pgClient.delete(LANGUAGES_TABLE, languageId, deleteReply -> {
							if (deleteReply.failed()) {
								String message = logAndSaveError(deleteReply.cause());
								asyncResultHandler.handle(Future.succeededFuture(DeleteLanguagesByLanguageIdResponse
										.respond500WithTextPlain(getErrorResponse(message))));
							} else {
								if (deleteReply.result().getUpdated() == 0) {
									asyncResultHandler.handle(Future.succeededFuture(
											DeleteLanguagesByLanguageIdResponse.respond404WithTextPlain("Not found")));
								} else {
									asyncResultHandler.handle(
											Future.succeededFuture(DeleteLanguagesByLanguageIdResponse.respond204()));
								}
							}
						});
					}
				});
			} catch (Exception e) {
				String message = logAndSaveError(e);
				asyncResultHandler.handle(Future.succeededFuture(
						DeleteLanguagesByLanguageIdResponse.respond500WithTextPlain(getErrorResponse(message))));
			}
		});
	}

	private Future<Boolean> checkLanguageInUse() {
		return Future.succeededFuture(false);
	}

}
