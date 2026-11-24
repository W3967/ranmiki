-- 工作台速览：示例插件
-- 可用的宿主 API：host.notes / host.tasks / host.docs / host.prompts / host.log
-- 每个 API 都要求 manifest 里申请了对应权限，否则调用会报错。

function lua_workbench_digest(args)
  local lines = {}

  local tasks = host.tasks.list()
  if #tasks == 0 then
    lines[#lines + 1] = "待办：清空了"
  else
    lines[#lines + 1] = "待办 " .. #tasks .. " 项"
    for i = 1, math.min(#tasks, 5) do
      local mark = "中"
      if tasks[i].priority == 2 then mark = "高" elseif tasks[i].priority == 0 then mark = "低" end
      lines[#lines + 1] = "- " .. tasks[i].title .. "（" .. mark .. "）"
    end
  end

  local notes = host.notes.list()
  lines[#lines + 1] = "笔记 " .. #notes .. " 篇"

  host.log("digest 完成：" .. #tasks .. " 个待办，" .. #notes .. " 篇笔记")
  return table.concat(lines, "\n")
end

-- P165：插件界面。
-- 宿主只认 title / text / notice / card / list / button / divider 七种节点。
function page_main(args)
  local tasks = host.tasks.list()
  local notes = host.notes.list()

  local task_lines = {}
  for i = 1, math.min(#tasks, maxLines) do
    task_lines[#task_lines + 1] = tasks[i].title
  end
  if #task_lines == 0 then task_lines[1] = "（没有待办）" end

  local greeting = host.config("greeting", "你好")
  local showTasks = host.config("showTasks", "true") == "true"
  local maxLines = tonumber(host.config("maxLines", "3")) or 3
  local mode = host.config("mode", "简洁")

  return {
    { type = "title",  text = greeting .. " · 工作台速览" },
    { type = "text",   text = "这是示例插件用 Lua 描述的界面，由宿主渲染。" },
    { type = "divider" },
    { type = "card",   title = "待办 " .. #tasks .. " 项", body = "最近几条见下" },
    { type = "list",   items = task_lines },
    { type = "card",   title = "笔记 " .. #notes .. " 篇", body = "全部笔记数量" },
    { type = "notice", text = "当前风格：" .. mode .. "；这个页面只能读。" },
    { type = "button", text = "刷新一下", action = "page_main" },
  }
end
