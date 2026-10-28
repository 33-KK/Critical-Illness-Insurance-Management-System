<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Plus, RefreshCw, Info } from "lucide-vue-next";
import { get, post, put, remove, money, showError, query } from "../api";
import type { Row } from "../api";
import { has, auth, loadUser } from "../auth";
import { schemas } from "../resourceSchemas";
import type { Field } from "../resourceSchemas";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
const props = defineProps<{ resource: string }>(),
  schema = computed(() => schemas[props.resource]!),
  rows = ref<Row[]>([]),
  loading = ref(false),
  keyword = ref(""),
  page = ref(1),
  dialog = ref(false),
  saving = ref(false),
  form = reactive<Row>({}),
  formRef = ref(),
  orgs = ref<Row[]>([]),
  permissions = ref<Row[]>([]),
  passwordOpen = ref(false),
  password = ref(""),
  passwordUser = ref<Row>(),
  passwordBusy = ref(false);
const paged = computed(() => rows.value.slice((page.value - 1) * 10, page.value * 10)),
  canManage = computed(() => has(schema.value.permission)),
  softDelete = computed(() => ["plans", "surgeries"].includes(props.resource));
const rules = computed(() =>
  Object.fromEntries(
    schema.value.fields
      .filter((f) => f.required)
      .map((f) => [
        f.key,
        [
          {
            required: true,
            message: `请${f.type === "select" ? "选择" : "填写"}${f.label}`,
            trigger: f.type === "select" ? "change" : "blur",
          },
        ],
      ]),
  ),
);
function options(field: Field) {
  if (field.key === "orgId") return orgs.value.map((o) => ({ label: o.name, value: o.id }));
  if (field.key === "parentId")
    return rows.value.filter((r) => r.id !== form.id).map((r) => ({ label: r.name, value: r.id }));
  if (field.key === "permission")
    return permissions.value.map((p) => ({
      label: `${p.name} (${p.code})`,
      value: p.code,
    }));
  return field.options || [];
}
async function load() {
  loading.value = true;
  try {
    rows.value = await get(schema.value.endpoint + query({ keyword: keyword.value }));
    page.value = 1;
  } catch (e) {
    showError(e);
  } finally {
    loading.value = false;
  }
}
function edit(row?: Row) {
  Object.keys(form).forEach((k) => delete form[k]);
  Object.assign(form, row ? { ...row, password: "" } : { ...schema.value.defaults });
  if (props.resource === "plans" && row)
    for (const k of ["rateA", "rateB", "rateC"])
      form[k] = Math.round(Number(form[k]) * 10000) / 100;
  dialog.value = true;
  setTimeout(() => formRef.value?.clearValidate(), 0);
}
async function save() {
  if (saving.value) return;
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  if (props.resource === "users" && !form.id && !form.password) {
    ElMessage.warning("请为新用户设置登录密码");
    return;
  }
  if (props.resource === "users" && form.password && form.password.length < 8) {
    ElMessage.warning("登录密码至少8位");
    return;
  }
  saving.value = true;
  try {
    const data = Object.fromEntries(
      schema.value.fields.map((f) => [f.key, form[f.key] === undefined ? null : form[f.key]]),
    );
    if (props.resource === "plans")
      for (const k of ["rateA", "rateB", "rateC"]) data[k] = Number((data[k] / 100).toFixed(4));
    if (props.resource === "users" && form.id && !data.password) delete data.password;
    if (form.id) await put(`${schema.value.endpoint}/${form.id}`, data);
    else await post(schema.value.endpoint, data);
    ElMessage.success(`${schema.value.singular}已保存`);
    dialog.value = false;
    await load();
    if (props.resource === "menus") auth.menus = await get("/menus");
  } catch (e) {
    showError(e);
  } finally {
    saving.value = false;
  }
}
async function del(row: Row) {
  try {
    await ElMessageBox.confirm(
      softDelete.value
        ? `确认停用“${row.name}”？停用后不再用于新业务，历史记录仍将保留。`
        : `确认删除“${row.name || row.label || row.code}”？已被引用的数据无法删除，可改为停用。`,
      `${softDelete.value ? "停用" : "删除"}${schema.value.singular}`,
      {
        type: "warning",
        confirmButtonText: softDelete.value ? "确认停用" : "确认删除",
        cancelButtonText: "取消",
      },
    );
    await remove(`${schema.value.endpoint}/${row.id}`);
    ElMessage.success(softDelete.value ? "已停用" : "已删除");
    await load();
    if (props.resource === "menus") auth.menus = await get("/menus");
  } catch (e) {
    if (e instanceof Error) showError(e);
  }
}
async function resetPassword() {
  if (passwordBusy.value) return;
  if (password.value.length < 8) {
    ElMessage.warning("新密码至少8位");
    return;
  }
  passwordBusy.value = true;
  try {
    await put(`/admin/users/${passwordUser.value?.id}/password`, {
      password: password.value,
    });
    ElMessage.success("登录密码已重置");
    passwordOpen.value = false;
  } catch (e) {
    showError(e);
  } finally {
    passwordBusy.value = false;
  }
}
function cell(row: Row, col: Row) {
  if (col.type === "money") return "¥ " + money(row[col.key]);
  if (col.type === "percent") return Math.round(Number(row[col.key]) * 10000) / 100 + "%";
  if (col.type === "orgType")
    return (
      ({ SYSTEM: "系统机构", HOSPITAL: "医疗机构", INSURER: "保险机构" } as Row)[row[col.key]] ||
      row[col.key]
    );
  if (col.type === "parent") return rows.value.find((r) => r.id === row[col.key])?.name || "无上级";
  return row[col.key] ?? "—";
}
onMounted(async () => {
  await load();
  try {
    if (props.resource === "users") orgs.value = await get("/admin/orgs");
    if (props.resource === "menus") permissions.value = await get("/admin/permissions");
  } catch (e) {
    showError(e);
  }
});
</script>
<template>
  <PageHeader :title="schema.title" :description="schema.description">
    <el-button v-if="canManage" type="primary" @click="edit()">
      <Plus :size="16" />
      新增{{ schema.singular }}
    </el-button>
  </PageHeader>
  <div v-if="resource === 'plans' || resource === 'surgeries'" class="info-note">
    <Info :size="17" />
    <span>
      {{
        resource === "plans"
          ? "报销比例根据 A / B / C 类门诊分别配置，历史就诊保留原始费率。"
          : "停用的手术不再用于新就诊，已登记的手术费用快照保持不变。"
      }}
    </span>
    <span class="info-note-right">{{ rows.length }} 个{{ schema.singular }}</span>
  </div>
  <section class="panel">
    <div class="table-toolbar">
      <el-input
        v-model="keyword"
        :placeholder="`搜索${schema.singular}名称或编码`"
        clearable
        class="search-input"
        @keyup.enter="load"
        @clear="load"
      >
        <template #prefix><Search :size="16" /></template>
      </el-input>
      <el-button @click="load">查询</el-button>
      <el-button
        text
        @click="
          keyword = '';
          load();
        "
      >
        <RefreshCw :size="15" />
        重置
      </el-button>
      <span class="toolbar-count">共 {{ rows.length }} 条记录</span>
    </div>
    <el-table v-loading="loading" :data="paged" row-key="id">
      <el-table-column
        v-for="col in schema.columns"
        :key="col.key"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <StatusBadge v-if="col.type === 'status'" :value="row[col.key]" />
          <span
            v-else
            :class="
              col.type === 'money' ? 'table-money' : col.type === 'percent' ? 'rate-value' : ''
            "
          >
            {{ cell(row, col) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        v-if="canManage"
        label="操作"
        :width="resource === 'users' ? 160 : 130"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button link type="primary" @click="edit(row)">编辑</el-button>
          <el-button
            v-if="resource === 'users'"
            link
            type="warning"
            @click="
              passwordUser = row;
              password = '';
              passwordOpen = true;
            "
          >
            重置密码
          </el-button>
          <el-button v-else-if="schema.deletable" link type="danger" @click="del(row)">
            {{ softDelete ? "停用" : "删除" }}
          </el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty :description="`暂无符合条件的${schema.singular}`" /></template>
    </el-table>
    <div class="pagination">
      <span>{{ canManage ? "配置变更将记录在操作日志中" : "您当前拥有查看权限" }}</span>
      <el-pagination
        v-model:current-page="page"
        :page-size="10"
        :total="rows.length"
        layout="total, prev, pager, next"
      />
    </div>
  </section>
  <el-dialog
    v-model="dialog"
    :title="(form.id ? '编辑' : '新增') + schema.singular"
    width="650px"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item
          v-for="field in schema.fields"
          :key="field.key"
          :label="field.label"
          :prop="field.key"
          :class="{ 'grid-full': field.full }"
        >
          <el-switch
            v-if="field.type === 'switch'"
            v-model="form[field.key]"
            active-text="启用"
            inactive-text="停用"
          />
          <el-input-number
            v-else-if="field.type === 'number'"
            v-model="form[field.key]"
            :min="field.min"
            :max="field.max"
            :precision="field.precision"
            :controls="false"
            class="full-width"
          />
          <el-select
            v-else-if="field.type === 'select'"
            v-model="form[field.key]"
            :placeholder="field.placeholder || '请选择'"
            filterable
            :clearable="!field.required"
          >
            <el-option
              v-for="o in options(field)"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
          <el-input
            v-else-if="field.type === 'textarea'"
            v-model="form[field.key]"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
          />
          <el-input
            v-else
            v-model="form[field.key]"
            :type="field.type === 'password' ? 'password' : 'text'"
            :show-password="field.type === 'password'"
            :placeholder="field.placeholder || `请输入${field.label}`"
            :disabled="resource === 'users' && field.key === 'username' && !!form.id"
            :maxlength="field.key === 'email' ? 150 : 100"
          />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">
        保存{{ schema.singular }}
      </el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="passwordOpen" title="重置登录密码" width="440px">
    <p class="muted">
      为 {{ passwordUser?.displayName }}（{{ passwordUser?.username }}）设置新密码。
    </p>
    <el-input
      v-model="password"
      type="password"
      show-password
      placeholder="请输入至少8位新密码"
      size="large"
      class="mt16"
    />
    <template #footer>
      <el-button @click="passwordOpen = false">取消</el-button>
      <el-button type="primary" :loading="passwordBusy" @click="resetPassword">确认重置</el-button>
    </template>
  </el-dialog>
</template>
