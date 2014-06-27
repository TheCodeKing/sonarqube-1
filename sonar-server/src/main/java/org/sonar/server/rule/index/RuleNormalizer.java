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
package org.sonar.server.rule.index;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.support.replication.ReplicationType;
import org.elasticsearch.action.update.UpdateRequest;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.debt.DebtCharacteristic;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.rule.RuleDto;
import org.sonar.core.rule.RuleParamDto;
import org.sonar.core.technicaldebt.db.CharacteristicDto;
import org.sonar.server.db.DbClient;
import org.sonar.server.search.BaseNormalizer;
import org.sonar.server.search.IndexDefinition;
import org.sonar.server.search.IndexField;
import org.sonar.server.search.Indexable;
import org.sonar.server.search.es.ListUpdate;

import java.lang.reflect.Field;
import java.util.*;

public class RuleNormalizer extends BaseNormalizer<RuleDto, RuleKey> {

  public static final class RuleParamField extends Indexable {

    public static final IndexField NAME = add(IndexField.Type.STRING, "name");
    public static final IndexField TYPE = add(IndexField.Type.STRING, "type");
    public static final IndexField DESCRIPTION = addSearchable(IndexField.Type.TEXT, "description");
    public static final IndexField DEFAULT_VALUE = add(IndexField.Type.STRING, "defaultValue");
    public static final Set<IndexField> ALL_FIELDS = getAllFields();

    private static Set<IndexField> getAllFields() {
      Set<IndexField> fields = new HashSet<IndexField>();
      for (Field classField : RuleParamField.class.getDeclaredFields()) {
        if (classField.getType().isAssignableFrom(IndexField.class)) {
          try {
            fields.add(IndexField.class.cast(classField.get(null)));
          } catch (IllegalAccessException e) {
            throw new IllegalStateException("Can not introspect rule index fields", e);
          }
        }
      }
      return fields;
    }
  }

  public static final class RuleField extends Indexable {

    /**
     * @deprecated because key should be used instead of id. This field is kept for compatibility with
     * SQALE console.
     */
    @Deprecated
    public static final IndexField ID = addSortable(IndexField.Type.NUMERIC, "id");

    public static final IndexField KEY = addSortable(IndexField.Type.STRING, "key");
    public static final IndexField _KEY = add(IndexField.Type.STRING, "_key");
    public static final IndexField REPOSITORY = add(IndexField.Type.STRING, "repo");

    public static final IndexField NAME = addSortableAndSearchable(IndexField.Type.STRING, "name");
    public static final IndexField CREATED_AT = addSortable(IndexField.Type.DATE, "createdAt");
    public static final IndexField UPDATED_AT = addSortable(IndexField.Type.DATE, UPDATED_AT_FIELD);
    public static final IndexField HTML_DESCRIPTION = addSearchable(IndexField.Type.TEXT, "htmlDesc");
    public static final IndexField SEVERITY = add(IndexField.Type.STRING, "severity");
    public static final IndexField STATUS = add(IndexField.Type.STRING, "status");
    public static final IndexField FIX_DESCRIPTION = add(IndexField.Type.STRING, "effortToFix");
    public static final IndexField LANGUAGE = add(IndexField.Type.STRING, "lang");
    public static final IndexField TAGS = add(IndexField.Type.STRING, "tags");
    public static final IndexField SYSTEM_TAGS = add(IndexField.Type.STRING, "sysTags");
    public static final IndexField INTERNAL_KEY = add(IndexField.Type.STRING, "internalKey");
    public static final IndexField IS_TEMPLATE = add(IndexField.Type.BOOLEAN, "isTemplate");
    public static final IndexField TEMPLATE_KEY = add(IndexField.Type.STRING, "templateKey");
    public static final IndexField DEFAULT_DEBT_FUNCTION_TYPE = add(IndexField.Type.STRING, "_debtRemFnType");
    public static final IndexField DEFAULT_DEBT_FUNCTION_COEFFICIENT = add(IndexField.Type.STRING, "_debtRemFnCoefficient");
    public static final IndexField DEFAULT_DEBT_FUNCTION_OFFSET = add(IndexField.Type.STRING, "_debtRemFnOffset");
    public static final IndexField DEBT_FUNCTION_TYPE = add(IndexField.Type.STRING, "debtRemFnType");
    public static final IndexField DEBT_FUNCTION_COEFFICIENT = add(IndexField.Type.STRING, "debtRemFnCoefficient");
    public static final IndexField DEBT_FUNCTION_OFFSET = add(IndexField.Type.STRING, "debtRemFnOffset");
    public static final IndexField DEFAULT_CHARACTERISTIC = add(IndexField.Type.STRING, "_debtChar");
    public static final IndexField DEFAULT_SUB_CHARACTERISTIC = add(IndexField.Type.STRING, "_debtSubChar");
    public static final IndexField CHARACTERISTIC = add(IndexField.Type.STRING, "debtChar");
    public static final IndexField SUB_CHARACTERISTIC = add(IndexField.Type.STRING, "debtSubChar");
    public static final IndexField NOTE = add(IndexField.Type.TEXT, "markdownNote");
    public static final IndexField NOTE_LOGIN = add(IndexField.Type.STRING, "noteLogin");
    public static final IndexField NOTE_CREATED_AT = add(IndexField.Type.DATE, "noteCreatedAt");
    public static final IndexField NOTE_UPDATED_AT = add(IndexField.Type.DATE, "noteUpdatedAt");
    public static final IndexField _TAGS = addSearchable(IndexField.Type.STRING, "_tags");
    public static final IndexField PARAMS = addEmbedded("params", RuleParamField.ALL_FIELDS);


    public static Set<IndexField> ALL_FIELDS = getAllFields();

    private static Set<IndexField> getAllFields() {
      Set<IndexField> fields = new HashSet<IndexField>();
      for (Field classField : RuleField.class.getDeclaredFields()) {
        if (classField.getType().isAssignableFrom(IndexField.class)) {
          try {
            fields.add(IndexField.class.cast(classField.get(null)));
          } catch (IllegalAccessException e) {
            e.printStackTrace();
          }
        }
      }
      return fields;
    }

    public static IndexField of(String fieldName) {
      for (IndexField field : ALL_FIELDS) {
        if (field.field().equals(fieldName)) {
          return field;
        }
      }
      return null;
    }
  }


  public RuleNormalizer(DbClient db) {
    super(IndexDefinition.RULE, db);
  }


  @Override
  public List<UpdateRequest> normalize(RuleKey key) {
    DbSession dbSession = db.openSession(false);
    try {
      List<UpdateRequest> requests = new ArrayList<UpdateRequest>();
      requests.addAll(normalize(db.ruleDao().getNullableByKey(dbSession, key)));
      for (RuleParamDto param : db.ruleDao().findRuleParamsByRuleKey(dbSession, key)) {
        requests.addAll(normalizeNested(param, key));
      }
      return requests;
    } finally {
      dbSession.close();
    }
  }

  @Override
  public List<UpdateRequest> normalize(RuleDto rule) {

    DbSession session = db.openSession(false);
    try {

      /** Update Fields */
      Map<String, Object> update = new HashMap<String, Object>();

      update.put(RuleField.ID.field(), rule.getId());

      update.put(RuleField.KEY.field(), rule.getKey().toString());
      update.put(RuleField._KEY.field(), ImmutableList.of(rule.getKey().repository(), rule.getKey().rule()));

      update.put(RuleField.REPOSITORY.field(), rule.getRepositoryKey());
      update.put(RuleField.NAME.field(), rule.getName());
      update.put(RuleField.CREATED_AT.field(), rule.getCreatedAt());
      update.put(RuleField.UPDATED_AT.field(), rule.getUpdatedAt());
      update.put(RuleField.HTML_DESCRIPTION.field(), rule.getDescription());
      update.put(RuleField.FIX_DESCRIPTION.field(), rule.getEffortToFixDescription());
      update.put(RuleField.SEVERITY.field(), rule.getSeverityString());
      update.put(RuleField.STATUS.field(), rule.getStatus().name());
      update.put(RuleField.LANGUAGE.field(), rule.getLanguage());
      update.put(RuleField.INTERNAL_KEY.field(), rule.getConfigKey());
      update.put(RuleField.IS_TEMPLATE.field(), rule.isTemplate());

      update.put(RuleField.NOTE.field(), rule.getNoteData());
      update.put(RuleField.NOTE_LOGIN.field(), rule.getNoteUserLogin());
      update.put(RuleField.NOTE_CREATED_AT.field(), rule.getNoteCreatedAt());
      update.put(RuleField.NOTE_UPDATED_AT.field(), rule.getNoteUpdatedAt());

      //TODO Legacy PARENT_ID in DTO should be parent_key
      Integer templateId = rule.getTemplateId();
      if (templateId != null) {
        RuleDto templateRule = db.ruleDao().getById(session, templateId);
        update.put(RuleField.TEMPLATE_KEY.field(), templateRule.getKey().toString());
      } else {
        update.put(RuleField.TEMPLATE_KEY.field(), null);
      }

      //TODO Legacy ID in DTO should be Key
      update.put(RuleField.CHARACTERISTIC.field(), null);
      update.put(RuleField.SUB_CHARACTERISTIC.field(), null);
      update.put(RuleField.DEFAULT_CHARACTERISTIC.field(), null);
      update.put(RuleField.DEFAULT_SUB_CHARACTERISTIC.field(), null);

      update.put(RuleField.DEFAULT_CHARACTERISTIC.field(), null);
      update.put(RuleField.DEFAULT_SUB_CHARACTERISTIC.field(), null);
      if (rule.getDefaultSubCharacteristicId() != null) {
        CharacteristicDto characteristic = null;
        CharacteristicDto subCharacteristic = null;
        subCharacteristic = db.debtCharacteristicDao().selectById(rule.getDefaultSubCharacteristicId(), session);
        if (subCharacteristic != null) {
          characteristic = db.debtCharacteristicDao().selectById(subCharacteristic.getParentId());
          update.put(RuleField.DEFAULT_CHARACTERISTIC.field(), characteristic.getKey());
          update.put(RuleField.DEFAULT_SUB_CHARACTERISTIC.field(), subCharacteristic.getKey());
        }
      }

      if (rule.getSubCharacteristicId() != null) {
        if (rule.getSubCharacteristicId() == -1) {
          update.put(RuleField.CHARACTERISTIC.field(), DebtCharacteristic.NONE);
          update.put(RuleField.SUB_CHARACTERISTIC.field(), DebtCharacteristic.NONE);
        } else {
          CharacteristicDto characteristic = null;
          CharacteristicDto subCharacteristic = null;
          subCharacteristic = db.debtCharacteristicDao().selectById(rule.getSubCharacteristicId(), session);
          characteristic = db.debtCharacteristicDao().selectById(subCharacteristic.getParentId());
          update.put(RuleField.CHARACTERISTIC.field(), characteristic.getKey());
          update.put(RuleField.SUB_CHARACTERISTIC.field(), subCharacteristic.getKey());
        }
      } else {
        update.put(RuleField.CHARACTERISTIC.field(), null);
        update.put(RuleField.SUB_CHARACTERISTIC.field(), null);
      }


      if (rule.getDefaultRemediationFunction() != null) {
        update.put(RuleField.DEFAULT_DEBT_FUNCTION_TYPE.field(), rule.getDefaultRemediationFunction());
        update.put(RuleField.DEFAULT_DEBT_FUNCTION_COEFFICIENT.field(), rule.getDefaultRemediationCoefficient());
        update.put(RuleField.DEFAULT_DEBT_FUNCTION_OFFSET.field(), rule.getDefaultRemediationOffset());
      } else {
        update.put(RuleField.DEFAULT_DEBT_FUNCTION_TYPE.field(), null);
        update.put(RuleField.DEFAULT_DEBT_FUNCTION_COEFFICIENT.field(), null);
        update.put(RuleField.DEFAULT_DEBT_FUNCTION_OFFSET.field(), null);
      }

      if (rule.getRemediationFunction() != null) {
        update.put(RuleField.DEBT_FUNCTION_TYPE.field(), rule.getRemediationFunction());
        update.put(RuleField.DEBT_FUNCTION_COEFFICIENT.field(), rule.getRemediationCoefficient());
        update.put(RuleField.DEBT_FUNCTION_OFFSET.field(), rule.getRemediationOffset());
      } else {
        update.put(RuleField.DEBT_FUNCTION_TYPE.field(), null);
        update.put(RuleField.DEBT_FUNCTION_COEFFICIENT.field(), null);
        update.put(RuleField.DEBT_FUNCTION_OFFSET.field(), null);
      }


      update.put(RuleField.TAGS.field(), rule.getTags());
      update.put(RuleField.SYSTEM_TAGS.field(), rule.getSystemTags());
      update.put(RuleField._TAGS.field(), Sets.union(rule.getSystemTags(), rule.getTags()));


      /** Upsert elements */
      Map<String, Object> upsert = new HashMap<String, Object>(update);
      upsert.put(RuleField.KEY.field(), rule.getKey().toString());
      upsert.put(RuleField.PARAMS.field(), new ArrayList<String>());


      /** Creating updateRequest */
      return ImmutableList.of(new UpdateRequest()
        .replicationType(ReplicationType.ASYNC)
        .consistencyLevel(WriteConsistencyLevel.QUORUM)
        .id(rule.getKey().toString())
        .doc(update)
        .upsert(upsert));

    } finally {
      session.close();
    }
  }

  @Override
  public List<UpdateRequest> normalizeNested(Object object, RuleKey key) {
    Preconditions.checkNotNull(key, "key of rule must be set");
    if (object.getClass().isAssignableFrom(RuleParamDto.class)) {
      return nestedUpdate((RuleParamDto) object, key);
    } else {
      throw new IllegalStateException("Cannot normalize object of type '" + object.getClass() + "' in current context");
    }
  }

  @Override
  public List<UpdateRequest> deleteNested(Object object, RuleKey key) {
    Preconditions.checkNotNull(key, "key of Rule must be set");
    if (object.getClass().isAssignableFrom(RuleParamDto.class)) {
      return nestedDelete((RuleParamDto) object, key);
    } else {
      throw new IllegalStateException("Cannot normalize object of type '" + object.getClass() + "' in current context");
    }
  }

  private List<UpdateRequest> nestedUpdate(RuleParamDto param, RuleKey key) {
    Map<String, Object> newParam = new HashMap<String, Object>();
    newParam.put(RuleParamField.NAME.field(), param.getName());
    newParam.put(RuleParamField.TYPE.field(), param.getType());
    newParam.put(RuleParamField.DESCRIPTION.field(), param.getDescription());
    newParam.put(RuleParamField.DEFAULT_VALUE.field(), param.getDefaultValue());

    return ImmutableList.of(new UpdateRequest()
        .id(key.toString())
        .script(ListUpdate.NAME)
        .addScriptParam(ListUpdate.FIELD, RuleField.PARAMS.field())
        .addScriptParam(ListUpdate.VALUE, newParam)
        .addScriptParam(ListUpdate.ID_FIELD, RuleParamField.NAME.field())
        .addScriptParam(ListUpdate.ID_VALUE, param.getName())
    );
  }

  private List<UpdateRequest> nestedDelete(RuleParamDto param, RuleKey key) {
    return ImmutableList.of(new UpdateRequest()
        .id(key.toString())
        .script(ListUpdate.NAME)
        .addScriptParam(ListUpdate.FIELD, RuleField.PARAMS.field())
        .addScriptParam(ListUpdate.VALUE, null)
        .addScriptParam(ListUpdate.ID_FIELD, RuleParamField.NAME.field())
        .addScriptParam(ListUpdate.ID_VALUE, param.getName())
    );
  }
}