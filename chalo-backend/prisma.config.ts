import { defineConfig } from 'prisma/config'
import 'dotenv/config'

export default defineConfig({
  migrate: {
    url: process.env.DATABASE_URL!,
  },
})
