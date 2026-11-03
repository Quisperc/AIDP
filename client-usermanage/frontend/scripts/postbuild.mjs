/**
 * 构建后处理：把 index.html 复制一份为 404.html。
 *
 * EdgeOne Pages（以及多数静态托管）在没有 rewrite 规则时，用 404.html 兜底，
 * 也就是 SPA 刷新 /users、/clients 这类前端路由时返回入口页而不是平台 404。
 * 之前这一步是手工 cp，放到构建脚本里，CI 构建（EdgeOne 关联 Git 仓库）才能自动完成。
 */
import { copyFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const dist = join(process.cwd(), 'dist')
const indexFile = join(dist, 'index.html')

if (!existsSync(indexFile)) {
  console.error('[postbuild] 找不到 dist/index.html，构建可能失败了')
  process.exit(1)
}

copyFileSync(indexFile, join(dist, '404.html'))
console.log('[postbuild] 已生成 dist/404.html（SPA 路由兜底）')
