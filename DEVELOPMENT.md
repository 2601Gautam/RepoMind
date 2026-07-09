# RepoMind Frontend Development Guide

## Project Overview

**RepoMind** is an AI-powered code understanding platform. Users:
1. Paste GitHub repository URLs
2. We index the codebase with semantic embeddings
3. Users chat with their code (ask questions, generate interview prep, debug)

## Technology Stack

- **Framework**: React 18 + Vite
- **Styling**: Tailwind CSS v3
- **Routing**: React Router v6
- **Build**: Vite
- **Package Manager**: npm
- **Auth**: Custom auth context (built with backend)

## Project Structure

```
frontend/
├── src/
│   ├── pages/
│   │   ├── LandingPage.jsx         ← Main landing page (REDESIGNED)
│   │   ├── DashboardPage.jsx       ← User dashboard with repo list
│   │   ├── ChatPage.jsx            ← Chat with codebase
│   │   ├── InterviewPage.jsx       ← Interview prep questions
│   │   ├── DebugPage.jsx           ← Debugging assistant
│   │   ├── LoginPage.jsx           ← Login form
│   │   ├── RegisterPage.jsx        ← Registration form
│   │   ├── ProfilePage.jsx         ← User profile
│   │   └── OAuthCallbackPage.jsx   ← OAuth redirect handler
│   ├── components/
│   │   ├── layout/
│   │   │   ├── Header.jsx          ← Top header (legacy)
│   │   │   └── NavBar.jsx          ← Navigation bar
│   │   ├── chat/
│   │   │   ├── ChatInput.jsx       ← Message input
│   │   │   ├── MessageList.jsx     ← Message history
│   │   │   ├── MessageBubble.jsx   ← Individual message
│   │   │   ├── SuggestedQuestions.jsx
│   │   │   └── SourceBadges.jsx
│   │   ├── repo/
│   │   │   ├── RepoCard.jsx        ← Repository card
│   │   │   ├── RepoUrlInput.jsx    ← URL input form
│   │   │   └── IngestionProgress.jsx
│   │   └── common/
│   │       ├── EmptyState.jsx
│   │       ├── LoadingSpinner.jsx
│   │       ├── ProtectedRoute.jsx
│   │       └── RateLimitBanner.jsx
│   ├── context/
│   │   └── AuthContext.jsx         ← Authentication state
│   ├── api/                        ← API client utilities
│   ├── App.jsx                     ← Route definitions
│   ├── App.css                     ← App-level styles (minimal)
│   ├── index.css                   ← Global styles & theme
│   └── main.jsx                    ← Entry point
├── tailwind.config.js              ← Theme configuration
├── vite.config.js
└── package.json
```

## Current Styling Setup

### Theme Configuration

**File**: `tailwind.config.js`
- Purple primary palette (#9333ea → #7e22ce)
- Neutral color system (#fafafa → #1a1a1a)
- Custom fonts, shadows, spacing
- Light mode optimized

### Global Styles

**File**: `index.css`
- Tailwind imports
- Base typography rules
- Scrollbar styling
- Focus states for accessibility
- Dark mode scrollbar (light theme)

### Minimal Component Styles

**File**: `App.css`
- Global transitions
- Text rendering improvements
- Interactive element animations

## Tailwind Usage Patterns

### Layout

```jsx
// Container with max-width and padding
<div className="max-w-4xl mx-auto px-6">
  {/* Content */}
</div>

// Spacing sections
<section className="py-20 md:py-32 bg-white">
  {/* Content with space above and below */}
</section>

// Flexible layouts
<div className="flex items-center justify-between gap-4">
  {/* Flexbox layout */}
</div>

// Grid layouts
<div className="grid grid-cols-1 md:grid-cols-3 gap-8">
  {/* Responsive 1 column → 3 columns */}
</div>
```

### Colors

```jsx
// Background colors
className="bg-white"              // Primary background
className="bg-neutral-50"         // Secondary background
className="bg-primary-600"        // Purple background

// Text colors
className="text-neutral-900"      // Dark text
className="text-neutral-600"      // Muted text
className="text-primary-600"      // Purple text

// Borders
className="border border-neutral-200"  // Light border
className="border-2 border-primary-600" // Purple border
```

### Interactive Elements

```jsx
// Buttons
<button className="px-4 py-2 bg-primary-600 text-white rounded-lg font-semibold hover:bg-primary-700 transition-colors">
  Click me
</button>

// Cards
<div className="bg-white border border-neutral-200 rounded-lg p-6 hover:shadow-md transition-shadow">
  {/* Card content */}
</div>

// Hover effects
className="hover:bg-neutral-100 transition-colors"
className="hover:shadow-lg transition-shadow"
```

### Responsive Design

```jsx
// Mobile-first responsive
className="text-2xl md:text-4xl"                // Font sizes
className="py-4 md:py-8"                       // Spacing
className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3"  // Layouts
className="hidden md:block"                    // Hide/show
```

## Common Development Tasks

### Adding a New Page

```jsx
// /frontend/src/pages/NewPage.jsx
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function NewPage() {
    const { user } = useAuth()
    
    return (
        <div className="min-h-screen bg-white">
            <header className="sticky top-0 z-50 bg-white border-b border-neutral-200">
                {/* Header content */}
            </header>
            
            <main className="max-w-4xl mx-auto px-6 py-12">
                {/* Page content */}
            </main>
        </div>
    )
}
```

Then add to `App.jsx` routes:
```jsx
import NewPage from './pages/NewPage'
{/* In AppRoutes */}
<Route path="/new-page" element={<ProtectedRoute><NewPage /></ProtectedRoute>} />
```

### Creating a Reusable Component

```jsx
// /frontend/src/components/common/Card.jsx
export default function Card({ title, description, children }) {
    return (
        <div className="bg-white border border-neutral-200 rounded-lg p-6 hover:shadow-md transition-shadow">
            {title && <h3 className="text-lg font-semibold text-neutral-900 mb-2">{title}</h3>}
            {description && <p className="text-neutral-600 text-sm mb-4">{description}</p>}
            {children}
        </div>
    )
}

// Usage
<Card title="My Card" description="Card description">
    {/* Content */}
</Card>
```

### Adding Purple Accents

Replace any instance of:
- `text-blue-*` → `text-primary-*`
- `bg-blue-*` → `bg-primary-*`
- `border-blue-*` → `border-primary-*`
- `hover:text-blue-*` → `hover:text-primary-*`

## Performance Considerations

1. **Tailwind CSS**: All styling is tree-shaken in production
2. **Image Optimization**: Use semantic naming and optimize size
3. **Component Splitting**: Keep components small and focused
4. **Lazy Loading**: Use React.lazy for route-based code splitting (already in place)
5. **Bundle Size**: Avoid adding large dependencies without justification

## Accessibility Standards

1. **Color Contrast**: WCAG AA compliant
2. **Focus States**: All interactive elements have focus rings
3. **Semantic HTML**: Use proper heading hierarchy
4. **ARIA Labels**: Add aria-labels where needed
5. **Keyboard Navigation**: All interactive elements keyboard accessible
6. **Alt Text**: Include descriptive alt text for images

## Testing Pages Against Theme

When updating pages, verify:
- [ ] Text color is `text-neutral-900` or `text-neutral-600` (not gray-*)
- [ ] Buttons use `bg-primary-600` (not blue-*)
- [ ] Borders use `border-neutral-200` (not gray-*)
- [ ] Backgrounds are `bg-white` or `bg-neutral-50` (not dark)
- [ ] Hover states have smooth transitions
- [ ] Page looks good on mobile (375px) and desktop (1920px)
- [ ] No console errors
- [ ] Loading states work correctly

## Environment Setup

### Required Environment Variables

Create `.env.local`:
```
VITE_API_URL=http://localhost:8080
```

### Running Locally

```bash
# Install dependencies
npm install

# Start dev server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

## Deployment

The frontend is deployed via GitHub and can be pushed to Vercel:

1. Changes are automatically deployed from the connected GitHub repo
2. Frontend is served from Vercel edge network
3. Backend API is called from `.env.local` configuration

## Git Workflow

```bash
# Create a feature branch
git checkout -b feature/my-feature

# Make changes, test locally
npm run dev

# Commit changes
git add .
git commit -m "📝 Your commit message"

# Push to GitHub
git push origin feature/my-feature

# Create a Pull Request on GitHub
```

## Common Issues & Solutions

### Styles Not Applying
- Clear browser cache: `Ctrl+Shift+Delete`
- Restart dev server: Stop and re-run `npm run dev`
- Check class names are spelled correctly
- Ensure `tailwind.config.js` is properly configured

### Build Errors
- Run `npm install` to ensure all dependencies installed
- Check for TypeScript/JavaScript syntax errors
- Verify no circular imports

### Performance Issues
- Use Chrome DevTools to profile
- Check for large images (optimize before uploading)
- Look for unnecessary re-renders in React DevTools

## Resources

- **Tailwind Docs**: https://tailwindcss.com/docs
- **React Router Docs**: https://reactrouter.com
- **Vite Docs**: https://vitejs.dev
- **RepoMind Backend**: See `/pom.xml` for Java backend

## Contact & Questions

For design system updates or styling questions, refer to:
- `.design-system.md` - Complete design specifications
- `REDESIGN_GUIDE.md` - Migration guide for existing pages
- `tailwind.config.js` - Theme configuration
