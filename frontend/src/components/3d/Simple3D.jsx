import React, { useRef } from 'react'
import { Canvas, useFrame } from '@react-three/fiber'
import * as THREE from 'three'

function RotatingBox() {
  const meshRef = useRef()

  useFrame(() => {
    if (meshRef.current) {
      meshRef.current.rotation.x += 0.001
      meshRef.current.rotation.y += 0.002
      meshRef.current.position.y = Math.sin(Date.now() * 0.001) * 0.3
    }
  })

  return (
    <mesh ref={meshRef}>
      <boxGeometry args={[2.5, 2.5, 0.2]} />
      <meshPhongMaterial color="#9333ea" emissive="#7c3aed" emissiveIntensity={0.3} />
    </mesh>
  )
}

function RotatingBox2() {
  const meshRef = useRef()

  useFrame(() => {
    if (meshRef.current) {
      meshRef.current.rotation.x -= 0.001
      meshRef.current.rotation.y -= 0.002
      meshRef.current.position.y = Math.sin(Date.now() * 0.001 + Math.PI / 2) * 0.3
    }
  })

  return (
    <mesh ref={meshRef} position={[-2.5, 0, 0]}>
      <boxGeometry args={[2.5, 2.5, 0.2]} />
      <meshPhongMaterial color="#a78bfa" emissive="#8b5cf6" emissiveIntensity={0.3} />
    </mesh>
  )
}

function RotatingBox3() {
  const meshRef = useRef()

  useFrame(() => {
    if (meshRef.current) {
      meshRef.current.rotation.x += 0.0015
      meshRef.current.rotation.y += 0.0025
      meshRef.current.position.y = Math.sin(Date.now() * 0.001 + Math.PI) * 0.3
    }
  })

  return (
    <mesh ref={meshRef} position={[2.5, 0, 0]}>
      <boxGeometry args={[2.5, 2.5, 0.2]} />
      <meshPhongMaterial color="#c4b5fd" emissive="#a78bfa" emissiveIntensity={0.3} />
    </mesh>
  )
}

export default function Simple3D() {
  return (
    <Canvas camera={{ position: [0, 0, 8], fov: 45 }}>
      <ambientLight intensity={0.6} color="#e9d5ff" />
      <pointLight position={[10, 10, 10]} intensity={0.8} color="#c4b5fd" />
      <pointLight position={[-10, -10, 5]} intensity={0.5} color="#a78bfa" />
      
      <RotatingBox />
      <RotatingBox2 />
      <RotatingBox3 />
    </Canvas>
  )
}
