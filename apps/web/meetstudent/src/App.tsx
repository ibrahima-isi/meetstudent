import { useState } from 'react';
import { LoginForm } from './components/LoginForm';
import { RegisterForm } from './components/RegisterForm';
import { EmailVerification } from './components/EmailVerification';
import { HomePage, School } from './components/HomePage';
import { SchoolDetailPage } from './components/SchoolDetailPage';
import { ProfilePage } from './components/ProfilePage';
import { LandingPage } from './components/LandingPage';

export default function App() {
  const [view, setView] = useState<'landing' | 'login' | 'register' | 'verify' | 'home' | 'school-detail' | 'profile'>('landing');
  const [userEmail, setUserEmail] = useState('');
  const [selectedSchool, setSelectedSchool] = useState<School | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  const handleRegisterSuccess = (email: string) => {
    setUserEmail(email);
    setView('verify');
  };

  const handleVerificationSuccess = () => {
    setView('login');
  };

  const handleLoginSuccess = () => {
    setIsAuthenticated(true);
    setView('home');
  };

  const handleLogout = () => {
    setIsAuthenticated(false);
    setView('landing');
    setUserEmail('');
    setSelectedSchool(null);
  };

  const handleSchoolClick = (school: School) => {
    setSelectedSchool(school);
    setView('school-detail');
  };

  const handleBackToHome = () => {
    setView(isAuthenticated ? 'home' : 'landing');
    setSelectedSchool(null);
  };

  const handleProfileClick = () => {
    if (isAuthenticated) {
      setView('profile');
    } else {
      setView('login');
    }
  };

  // Landing page - public view
  if (view === 'landing') {
    return (
      <LandingPage
        onSchoolClick={handleSchoolClick}
        onLoginClick={() => setView('login')}
        onRegisterClick={() => setView('register')}
      />
    );
  }

  // Authenticated home page
  if (view === 'home') {
    return (
      <HomePage 
        onLogout={handleLogout}
        onSchoolClick={handleSchoolClick}
        onProfileClick={handleProfileClick}
      />
    );
  }

  if (view === 'school-detail' && selectedSchool) {
    return (
      <SchoolDetailPage 
        school={selectedSchool}
        onBack={handleBackToHome}
        isAuthenticated={isAuthenticated}
        onLoginPrompt={() => setView('login')}
      />
    );
  }

  if (view === 'profile') {
    return (
      <ProfilePage 
        onBack={handleBackToHome}
      />
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {view === 'login' && (
          <LoginForm 
            onSwitchToRegister={() => setView('register')}
            onLoginSuccess={handleLoginSuccess}
          />
        )}
        {view === 'register' && (
          <RegisterForm 
            onSwitchToLogin={() => setView('login')}
            onRegisterSuccess={handleRegisterSuccess}
          />
        )}
        {view === 'verify' && (
          <EmailVerification 
            email={userEmail}
            onVerificationSuccess={handleVerificationSuccess}
          />
        )}
      </div>
    </div>
  );
}