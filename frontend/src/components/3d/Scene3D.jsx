import React, { useRef, useState } from 'react'
import { Canvas, useFrame } from '@react-three/fiber'
import { OrbitControls, PerspectiveCamera } from '@react-three/drei'
import * as THREE from 'three'

// Animated Code Block Mesh
function CodeBlockMesh({ position, rotation, delay }) {
  const meshRef = useRef()
  const [hovered, setHovered] = useState(false)

  useFrame(({ clock }) => {
    if (meshRef.current) {
      // Gentle floating animation
      meshRef.current.position.y = position[1] + Math.sin(clock.getElapsedTime() * 0.5 + delay) * 0.3
      
      // Slow rotation
      meshRef.current.rotation.x += 0.0005
      meshRef.current.rotation.y += 0.0008

      // Enhanced rotation when hovered
      if (hovered) {
        meshRef.current.rotation.x += 0.01
        meshRef.current.rotation.y += 0.015
      }
    }
  })

  return (
    <group ref={meshRef} position={position} rotation={rotation}>
      <mesh
        onPointerEnter={() => setHovered(true)}
        onPointerLeave={() => setHovered(false)}
        scale={hovered ? 1.1 : 1}
      >
        <boxGeometry args={[2.5, 2.5, 0.2]} />
        <meshPhongMaterial
          color={hovered ? '#8b5cf6' : '#9333ea'}
          emissive={hovered ? '#a78bfa' : '#7c3aed'}
          emissiveIntensity={hovered ? 0.4 : 0.2}
          shininess={100}
        />
      </mesh>

      {/* Glowing edge lines */}
      <lineSegments>
        <edgeGeometry attach="geometry" args={[new THREE.BoxGeometry(2.5, 2.5, 0.2)]} />
        <lineBasicMaterial
          color={hovered ? '#a78bfa' : '#c4b5fd'}
          linewidth={2}
          transparent
          opacity={hovered ? 0.8 : 0.4}
        />
      </lineSegments>
    </group>
  )
}

// Environment lighting and effects
function Scene() {
  return (
    <>
      <PerspectiveCamera makeDefault position={[0, 0, 6]} fov={50} />
      
      {/* Lighting setup */}
      <ambientLight intensity={0.6} color="#e9d5ff" />
      <pointLight position={[10, 10, 10]} intensity={0.8} color="#c4b5fd" />
      <pointLight position={[-10, -10, 5]} intensity={0.5} color="#a78bfa" />
      
      {/* Code blocks - arranged in 3D space */}
      <CodeBlockMesh
        position={[-2, 0, 0]}
        rotation={[0.3, -0.5, 0.1]}
        delay={0}
      />
      <CodeBlockMesh
        position={[0, 0.5, -1]}
        rotation={[-0.2, 0.3, -0.1]}
        delay={Math.PI / 2}
      />
      <CodeBlockMesh
        position={[2, -0.3, 0]}
        rotation={[0.1, 0.6, 0.2]}
        delay={Math.PI}
      />

      {/* Orbit controls for interactivity */}
      <OrbitControls
        enableZoom={false}
        enablePan={false}
        autoRotate
        autoRotateSpeed={2}
        maxPolarAngle={Math.PI * 0.6}
        minPolarAngle={Math.PI * 0.4}
      />
    </>
  )
}

export default function Scene3D() {
  return (
    <Canvas
      gl={{
        antialias: true,
        alpha: true,
        powerPreference: 'high-performance',
      }}
      camera={{ position: [0, 0, 6], fov: 50 }}
    >
      <Scene />
    </Canvas>
  )
}
