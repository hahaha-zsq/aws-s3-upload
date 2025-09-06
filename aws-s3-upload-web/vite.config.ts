import {defineConfig, loadEnv} from 'vite'
import path from "path";
import vue from '@vitejs/plugin-vue'
export default defineConfig(({mode}) => {
    const env = loadEnv(mode, process.cwd(), 'aws')
    console.log(env)
    return {
        plugins: [
            vue()
        ],
        resolve: {
            alias: {
                // 安装 pnpm i @types/node -D path来自这个包，这个是为了编译使用@符号不报错
                '@': path.resolve(__dirname, './src')
            }
        },
    }
})
