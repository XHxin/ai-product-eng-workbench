<script setup lang="ts">
const nodes = [
  { title: '需求录入', desc: '人工录入 / 钉钉 / AI听记 / 表格', cls: 'human' },
  { title: 'AI提取结构化', desc: '标题 / 类型 / 优先级建议', cls: 'ai' },
  { title: 'PM确认结果', desc: '确认并同步需求池', cls: 'decide' },
  { title: '纳入迭代', desc: '锁定本次迭代范围', cls: 'human' },
  { title: '需求分析', desc: '汇总迭代生成 PRD（原型+文档）', cls: 'ai' },
  { title: 'PRD确认', desc: '钉钉 / 听记 / 人工结论写入', cls: 'human' },
  { title: '需求评审', desc: '人工登记评审结果', cls: 'human' },
  { title: '最终PRD', desc: '评审通过形成最终版本', cls: 'final' },
  { title: '任务拆分', desc: 'AI建议，人工确认后交付', cls: 'ai' },
  { title: '开发协作', desc: '工时 / 技术方案 / 测试点', cls: 'human' },
  { title: '发布提醒', desc: '迭代完成触发人工确认', cls: 'decide' },
  { title: '已完成', desc: '发布记录与回写通知', cls: 'final' },
]
</script>
<template>
  <div class="view-head"><div><h1>业务流程</h1><div class="desc">端到端需求流转与 AI / 人工协作边界</div></div></div>
  <div class="panel">
    <div class="panel-h"><h3>端到端业务流程</h3><span class="sub">录入 → 分析 → 交付</span></div>
    <div class="panel-b">
      <div class="legend" style="margin-bottom:14px">
        <span><i style="background:#f5f3ff;border-color:#a78bfa" />AI自动处理</span>
        <span><i style="background:#eff6ff;border-color:#93c5fd" />人工确认/操作</span>
        <span><i style="background:#fff7ed;border-color:#fb923c" />决策/触发</span>
        <span><i style="background:#ecfdf5;border-color:#34d399" />闭环产出</span>
      </div>
      <div class="flow-steps">
        <div v-for="(node, index) in nodes" :key="node.title" :class="['flow-node', node.cls]">
          <b>{{ index + 1 }}. {{ node.title }}</b>
          <small>{{ node.desc }}</small>
        </div>
      </div>
    </div>
  </div>
  <div class="split-2" style="margin-top:16px">
    <div class="panel"><div class="panel-h"><h3>AI 可自动完成</h3></div><div class="panel-b" style="font-size:13px;line-height:2;color:var(--ink-2)">
      <div>• 将原始内容提取为结构化标准需求</div>
      <div>• 建议需求类型与 P0–P3 优先级及依据</div>
      <div>• 生成 PRD 原型 HTML 与需求文档</div>
      <div>• 汇总待确认问题与确认结论</div>
      <div>• 建议研发任务拆分与验收标准</div>
    </div></div>
    <div class="panel"><div class="panel-h"><h3>必须人工确认</h3></div><div class="panel-b" style="font-size:13px;line-height:2;color:var(--ink-2)">
      <div>• 确认 AI 提取结果（可编辑）</div>
      <div>• 确认并同步需求池、优先级与迭代范围</div>
      <div>• 确认待确认问题与最终 PRD</div>
      <div>• 登记评审结论并生成最终版 PRD</div>
      <div>• 确认研发任务、开发工时和技术方案</div>
      <div>• <b>执行发布</b>（强制人工）</div>
    </div></div>
  </div>
</template>
