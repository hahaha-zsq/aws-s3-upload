import {defineConfig, loadEnv} from 'vite'
import path from "path";
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig(({mode}) => {
    // 根据当前工作目录中的 `mode` 加载 .env 文件设置第三个参数为 '' 来加载所有环境变量
    // 根据指定模式加载环境变量
    // 参数 mode: 当前运行模式，例如 'development' 或 'production'
    // 参数 directory: 指定从哪个目录开始查找环境变量文件，默认为项目根目录
    // 参数 prefix: 环境变量的前缀，用于筛选哪些环境变量应该被加载
    const env = loadEnv(mode, process.cwd(), 'aws')
    console.log(env)
    return {
        plugins: [
            vue(),
        ],
        resolve: {
            alias: {
                // 安装 pnpm i @types/node -D path来自这个包，这个是为了编译使用@符号不报错
                '@': path.resolve(__dirname, './src')
            }
        },
    }
})
