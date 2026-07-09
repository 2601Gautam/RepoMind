import React, { Suspense } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import Simple3D from './Simple3D'

export default function Hero3D() {

  return (
    <section className="relative w-full bg-gradient-to-b from-neutral-50 via-primary-50/30 to-neutral-50 overflow-hidden">
      {/* Background gradients */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-0 left-1/4 w-96 h-96 bg-primary-200 rounded-full blur-3xl opacity-20" />
        <div className="absolute bottom-20 right-1/4 w-80 h-80 bg-primary-300 rounded-full blur-3xl opacity-15" />
      </div>

      <div className="relative max-w-6xl mx-auto px-6 py-16 md:py-32">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
          {/* Left: Text Content */}
          <motion.div
            className="space-y-8 z-10"
            initial={{ opacity: 0, x: -50 }}
            whileInView={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.8 }}
            viewport={{ once: true }}
          >
            <div className="space-y-4">
              <motion.div
                className="inline-flex items-center gap-2 bg-primary-100 border border-primary-300 rounded-full px-4 py-2"
                initial={{ opacity: 0, scale: 0.8 }}
                whileInView={{ opacity: 1, scale: 1 }}
                transition={{ delay: 0.2, duration: 0.5 }}
                viewport={{ once: true }}
              >
                <span className="w-2 h-2 rounded-full bg-primary-600 animate-pulse" />
                <span className="text-sm font-medium text-primary-700">3D-Powered AI Understanding</span>
              </motion.div>

              <motion.h1
                className="text-5xl md:text-6xl font-bold leading-tight"
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.3, duration: 0.7 }}
                viewport={{ once: true }}
              >
                <span className="text-neutral-900">Chat with your</span>
                <br />
                <span className="bg-gradient-to-r from-primary-600 via-primary-500 to-primary-700 bg-clip-text text-transparent">
                  code in 3D
                </span>
              </motion.h1>

              <motion.p
                className="text-lg text-neutral-600 max-w-lg leading-relaxed"
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.4, duration: 0.7 }}
                viewport={{ once: true }}
              >
                Experience the future of code understanding. Interactive 3D visualization meets powerful AI analysis. Ask questions about any GitHub repository and get answers rooted in actual code.
              </motion.p>
            </div>

            {/* CTA Buttons */}
            <motion.div
              className="flex flex-col sm:flex-row gap-4 pt-4"
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.5, duration: 0.7 }}
              viewport={{ once: true }}
            >
              <Link
                to="/register"
                className="px-8 py-4 bg-gradient-to-r from-primary-600 to-primary-700 text-white rounded-lg font-semibold hover:shadow-lg hover:shadow-primary-500/50 transition-all text-center transform hover:scale-105"
              >
                Start Exploring
              </Link>
              <Link
                to="/login"
                className="px-8 py-4 bg-white border-2 border-neutral-300 text-neutral-900 rounded-lg font-semibold hover:border-primary-600 hover:text-primary-600 transition-all text-center"
              >
                Sign In
              </Link>
            </motion.div>

            {/* Stats */}
            <motion.div
              className="grid grid-cols-3 gap-6 pt-8 border-t border-neutral-200"
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              transition={{ delay: 0.6, duration: 0.7 }}
              viewport={{ once: true }}
            >
              <div>
                <p className="text-2xl font-bold text-primary-600">10K+</p>
                <p className="text-sm text-neutral-600">Repos Analyzed</p>
              </div>
              <div>
                <p className="text-2xl font-bold text-primary-600">50M+</p>
                <p className="text-sm text-neutral-600">Code Files</p>
              </div>
              <div>
                <p className="text-2xl font-bold text-primary-600">99.9%</p>
                <p className="text-sm text-neutral-600">Accuracy</p>
              </div>
            </motion.div>
          </motion.div>

          {/* Right: 3D Scene */}
          <motion.div
            className="relative h-96 md:h-full min-h-96 rounded-2xl overflow-hidden bg-gradient-to-br from-neutral-50 to-primary-50 border border-neutral-200 shadow-xl"
            initial={{ opacity: 0, scale: 0.9 }}
            whileInView={{ opacity: 1, scale: 1 }}
            transition={{ delay: 0.2, duration: 0.8 }}
            viewport={{ once: true }}
          >
            <Suspense fallback={
              <div className="w-full h-full flex items-center justify-center">
                <div className="text-center space-y-3">
                  <div className="w-12 h-12 rounded-full bg-primary-200 animate-pulse mx-auto" />
                  <p className="text-neutral-600 text-sm">Loading 3D scene...</p>
                </div>
              </div>
            }>
              <Simple3D />
            </Suspense>

            {/* Corner decorations */}
            <div className="absolute top-4 right-4 w-12 h-12 border-t-2 border-r-2 border-primary-300 rounded-tr-lg pointer-events-none" />
            <div className="absolute bottom-4 left-4 w-12 h-12 border-b-2 border-l-2 border-primary-300 rounded-bl-lg pointer-events-none" />
          </motion.div>
        </div>
      </div>
    </section>
  )
}
