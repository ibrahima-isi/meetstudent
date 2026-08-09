import { useState } from 'react';
import { Mail, Lock, User, AlertCircle, UserPlus, GraduationCap, MapPin, Users, BookOpen } from 'lucide-react';

interface RegisterFormProps {
  onSwitchToLogin: () => void;
  onRegisterSuccess: (email: string) => void;
}

const SENEGAL_SPECIALTIES = [
  'Mathématiques',
  'Physique-Chimie',
  'Sciences de la Vie et de la Terre (SVT)',
  'Français',
  'Anglais',
  'Histoire-Géographie',
  'Philosophie',
  'Éducation Physique et Sportive (EPS)',
  'Sciences Économiques et Sociales (SES)',
  'Informatique',
  'Arts Plastiques',
  'Musique',
  'Arabe',
  'Espagnol',
  'Allemand',
  'Sciences Physiques',
  'Biologie',
  'Chimie',
  'Lettres Modernes',
  'Lettres Classiques',
];

export function RegisterForm({ onSwitchToLogin, onRegisterSuccess }: RegisterFormProps) {
  const [step, setStep] = useState(1);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [userType, setUserType] = useState<'student' | 'teacher' | ''>('');
  const [bacType, setBacType] = useState('');
  const [collegeLevel, setCollegeLevel] = useState('');
  const [specialty, setSpecialty] = useState('');
  const [filteredSpecialties, setFilteredSpecialties] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [town, setTown] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSpecialtyChange = (value: string) => {
    setSpecialty(value);
    if (value.trim()) {
      const filtered = SENEGAL_SPECIALTIES.filter(spec =>
        spec.toLowerCase().includes(value.toLowerCase())
      );
      setFilteredSpecialties(filtered);
      setShowSuggestions(true);
    } else {
      setFilteredSpecialties([]);
      setShowSuggestions(false);
    }
  };

  const selectSpecialty = (spec: string) => {
    setSpecialty(spec);
    setShowSuggestions(false);
    setFilteredSpecialties([]);
  };

  const handleNextStep = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // Validate step 1 fields
    if (!userType) {
      setError('Please select a user type.');
      return;
    }

    if (userType === 'student' && (!bacType || !collegeLevel)) {
      setError('Please complete all student fields.');
      return;
    }

    if (userType === 'teacher' && !specialty) {
      setError('Please enter your specialty.');
      return;
    }

    // Move to step 2
    setStep(2);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    // Validate passwords match
    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      setIsLoading(false);
      return;
    }

    // Validate password strength
    if (password.length < 8) {
      setError('Password must be at least 8 characters long.');
      setIsLoading(false);
      return;
    }

    // Simulate API call
    setTimeout(() => {
      // Check if user already exists
      const existingUser = localStorage.getItem(`user_${email}`);
      if (existingUser) {
        setError('An account with this email already exists.');
        setIsLoading(false);
        return;
      }

      // Store user data (mock)
      const userData = {
        name,
        email,
        password, // In real app, this would be hashed
        userType,
        ...(userType === 'student' && { bacType, collegeLevel }),
        ...(userType === 'teacher' && { specialty }),
        town,
        verified: false,
        verificationCode: Math.floor(100000 + Math.random() * 900000).toString(),
      };
      
      localStorage.setItem(`user_${email}`, JSON.stringify(userData));
      
      setIsLoading(false);
      onRegisterSuccess(email);
    }, 1000);
  };

  return (
    <div className="bg-white rounded-2xl shadow-xl p-8">
      <div className="text-center mb-8">
        <div className="inline-flex items-center justify-center w-16 h-16 bg-indigo-100 rounded-full mb-4">
          <UserPlus className="w-8 h-8 text-indigo-600" />
        </div>
        <h1 className="text-gray-900 mb-2">Create Account</h1>
        <p className="text-gray-600">
          {step === 1 ? 'Step 1 of 2: Basic Information' : 'Step 2 of 2: Set Your Password'}
        </p>
      </div>

      {/* Progress Indicator */}
      <div className="mb-6">
        <div className="flex gap-2">
          <div className={`flex-1 h-2 rounded-full ${step >= 1 ? 'bg-indigo-600' : 'bg-gray-200'}`}></div>
          <div className={`flex-1 h-2 rounded-full ${step >= 2 ? 'bg-indigo-600' : 'bg-gray-200'}`}></div>
        </div>
      </div>

      <form onSubmit={step === 1 ? handleNextStep : handleSubmit} className="space-y-5">
        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-start gap-3">
            <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
            <p className="text-red-800">{error}</p>
          </div>
        )}

        {step === 1 && (
          <>
            <div>
              <label htmlFor="name" className="block text-gray-700 mb-2">
                Full Name
              </label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                <input
                  id="name"
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                  placeholder="John Doe"
                  required
                />
              </div>
            </div>

            <div>
              <label htmlFor="email" className="block text-gray-700 mb-2">
                Email Address
              </label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                <input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                  placeholder="you@example.com"
                  required
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => setUserType('student')}
                className={`flex items-center justify-center gap-2 px-4 py-3 border-2 rounded-lg transition-all ${
                  userType === 'student'
                    ? 'border-indigo-600 bg-indigo-50 text-indigo-700'
                    : 'border-gray-300 hover:border-gray-400'
                }`}
              >
                <GraduationCap className="w-5 h-5" />
                <span>Student</span>
              </button>
              <button
                type="button"
                onClick={() => setUserType('teacher')}
                className={`flex items-center justify-center gap-2 px-4 py-3 border-2 rounded-lg transition-all ${
                  userType === 'teacher'
                    ? 'border-indigo-600 bg-indigo-50 text-indigo-700'
                    : 'border-gray-300 hover:border-gray-400'
                }`}
              >
                <Users className="w-5 h-5" />
                <span>Teacher</span>
              </button>
            </div>

            {userType === 'student' && (
              <>
                <div className="relative">
                  <GraduationCap className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                  <select
                    id="bacType"
                    value={bacType}
                    onChange={(e) => setBacType(e.target.value)}
                    className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent appearance-none bg-white"
                    required
                  >
                    <option value="">Select your BAC type</option>
                    <option value="bac-general">Bac Général</option>
                    <option value="bac-techno-sti2d">Bac Technologique - STI2D</option>
                    <option value="bac-techno-stmg">Bac Technologique - STMG</option>
                    <option value="bac-techno-st2s">Bac Technologique - ST2S</option>
                    <option value="bac-techno-stl">Bac Technologique - STL</option>
                    <option value="bac-techno-std2a">Bac Technologique - STD2A</option>
                    <option value="bac-techno-stav">Bac Technologique - STAV</option>
                    <option value="bac-techno-sthr">Bac Technologique - STHR</option>
                    <option value="bac-pro">Bac Professionnel</option>
                  </select>
                </div>

                <div>
                  <label htmlFor="collegeLevel" className="block text-gray-700 mb-2">
                    Level of College Studies
                  </label>
                  <div className="relative">
                    <BookOpen className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                    <select
                      id="collegeLevel"
                      value={collegeLevel}
                      onChange={(e) => setCollegeLevel(e.target.value)}
                      className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent appearance-none bg-white"
                      required
                    >
                      <option value="">Select your level</option>
                      <option value="l1">Licence 1 (L1)</option>
                      <option value="l2">Licence 2 (L2)</option>
                      <option value="l3">Licence 3 (L3)</option>
                      <option value="m1">Master 1 (M1)</option>
                      <option value="m2">Master 2 (M2)</option>
                      <option value="doctorat">Doctorat</option>
                    </select>
                  </div>
                </div>
              </>
            )}

            {userType === 'teacher' && (
              <div>
                <label htmlFor="specialty" className="block text-gray-700 mb-2">
                  Specialty
                </label>
                <div className="relative">
                  <BookOpen className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400 z-10" />
                  <input
                    id="specialty"
                    type="text"
                    value={specialty}
                    onChange={(e) => handleSpecialtyChange(e.target.value)}
                    onFocus={() => {
                      if (filteredSpecialties.length > 0) {
                        setShowSuggestions(true);
                      }
                    }}
                    onBlur={() => {
                      // Delay to allow clicking on suggestions
                      setTimeout(() => setShowSuggestions(false), 200);
                    }}
                    className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                    placeholder="Start typing your specialty..."
                    required
                  />
                  {showSuggestions && filteredSpecialties.length > 0 && (
                    <div className="absolute z-20 w-full mt-1 bg-white border border-gray-300 rounded-lg shadow-lg max-h-60 overflow-y-auto">
                      {filteredSpecialties.map((spec, index) => (
                        <button
                          key={index}
                          type="button"
                          onClick={() => selectSpecialty(spec)}
                          className="w-full text-left px-4 py-2 hover:bg-indigo-50 hover:text-indigo-700 transition-colors"
                        >
                          {spec}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}
          </>
        )}

        {step === 1 && (
          <div>
            <label htmlFor="town" className="block text-gray-700 mb-2">
              Town / City
            </label>
            <div className="relative">
              <MapPin className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
              <input
                id="town"
                type="text"
                value={town}
                onChange={(e) => setTown(e.target.value)}
                className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                placeholder="Dakar, Saint-Louis, Thiès..."
                required
              />
            </div>
          </div>
        )}

        {step === 1 && (
          <button
            type="submit"
            className="w-full flex items-center justify-center bg-indigo-600 text-white py-3 rounded-lg hover:bg-indigo-700 transition-colors"
          >
            Next Step
          </button>
        )}

        {step === 2 && (
          <>
            <div>
              <label htmlFor="password" className="block text-gray-700 mb-2">
                Password
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                <input
                  id="password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                  placeholder="••••••••"
                  required
                  minLength={8}
                />
              </div>
              <p className="text-gray-500 mt-1">Must be at least 8 characters</p>
            </div>

            <div>
              <label htmlFor="confirmPassword" className="block text-gray-700 mb-2">
                Confirm Password
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                <input
                  id="confirmPassword"
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                  placeholder="••••••••"
                  required
                />
              </div>
            </div>

            <div className="flex items-start">
              <input
                type="checkbox"
                id="terms"
                className="w-4 h-4 text-indigo-600 border-gray-300 rounded focus:ring-indigo-500 mt-1"
                required
              />
              <label htmlFor="terms" className="ml-2 text-gray-700">
                I agree to the{' '}
                <button type="button" className="text-indigo-600 hover:text-indigo-700">
                  Terms of Service
                </button>{' '}
                and{' '}
                <button type="button" className="text-indigo-600 hover:text-indigo-700">
                  Privacy Policy
                </button>
              </label>
            </div>

            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => {
                  setStep(1);
                  setError('');
                }}
                className="flex-1 flex items-center justify-center bg-gray-200 text-gray-700 py-3 rounded-lg hover:bg-gray-300 transition-colors"
              >
                Back
              </button>
              <button
                type="submit"
                disabled={isLoading}
                className="flex-1 flex items-center justify-center bg-indigo-600 text-white py-3 rounded-lg hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isLoading ? 'Creating Account...' : 'Create Account'}
              </button>
            </div>
          </>
        )}
      </form>

      <div className="mt-6 text-center">
        <p className="text-gray-600">
          Already have an account?{' '}
          <button
            onClick={onSwitchToLogin}
            className="text-indigo-600 hover:text-indigo-700"
          >
            Sign in
          </button>
        </p>
      </div>
    </div>
  );
}
